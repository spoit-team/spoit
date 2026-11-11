#Requires -Version 7.0
<#
.SYNOPSIS
서울시 공공서비스예약 체육시설 JSON으로 시설 등록 마이그레이션 SQL을 만든다.
.DESCRIPTION
원본 한 건은 시설이 아니라 예약 상품(코트·시간대)이라 장소 단위로 묶는다. 종목·지역·좌표 규칙과 아래 수정 목록을
적용해 마이그레이션 SQL과 검수용 CSV를 만든다. DB에는 접속하지 않는다.
원본 JSON과 검수용 CSV는 out/(git 무시 대상)에 두고 커밋하지 않는다.
.EXAMPLE
pwsh -File scripts/public-facility-import.ps1
#>
param(
    [string]$InputPath = 'out/public-facilities.json',
    [string]$OutputPath = 'db/migrations/20261110-seoul-public-facilities.sql',
    [string]$ReviewPath = 'out/public-facilities-review.csv'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$invariant = [Globalization.CultureInfo]::InvariantCulture

# 원본 소분류 → sport.name. 여기 없는 소분류(체육관·야구장·파크골프장 등)의 상품은 버린다.
$sportByClass = @{
    '테니스장' = '테니스'; '풋살장' = '축구+풋살'; '축구장' = '축구+풋살'
    '족구장' = '족구'; '농구장' = '농구'; '배드민턴장' = '배드민턴'
}

# sport_id 순서. 매칭방 기본 조건은 db/seed.sql의 같은 종목 시설에서 가장 많이 쓴 조합이고, 동률이면 이용료가 낮은 쪽이다.
$sports = @(
    [pscustomobject]@{ Name = '테니스'; Min = 2; Max = 4; Fee = 4000; Skill = '초급' }
    [pscustomobject]@{ Name = '축구+풋살'; Min = 6; Max = 12; Fee = 8000; Skill = '중급' }
    [pscustomobject]@{ Name = '족구'; Min = 4; Max = 8; Fee = 5000; Skill = '초급' }
    [pscustomobject]@{ Name = '농구'; Min = 4; Max = 10; Fee = 6000; Skill = '초급' }
    [pscustomobject]@{ Name = '배드민턴'; Min = 2; Max = 8; Fee = 5000; Skill = '초급' }
)

# 서울 25개 구와 region_id. seed의 번호가 행정구역 순번 + 4라서(종로구 5 … 송파구 28) 빈 번호를 같은 규칙으로 채운다.
$districtIds = [ordered]@{
    '종로구' = 5; '중구' = 6; '용산구' = 7; '성동구' = 8; '광진구' = 9; '동대문구' = 10; '중랑구' = 11
    '성북구' = 12; '강북구' = 13; '도봉구' = 14; '노원구' = 15; '은평구' = 16; '서대문구' = 17; '마포구' = 18
    '양천구' = 19; '강서구' = 20; '구로구' = 21; '금천구' = 22; '영등포구' = 23; '동작구' = 24; '관악구' = 25
    '서초구' = 26; '강남구' = 27; '송파구' = 28; '강동구' = 29
}
$seedDistricts = @('종로구', '성동구', '광진구', '노원구', '은평구', '마포구', '강서구', '영등포구', '관악구', '서초구', '송파구')

# 담당 구. 전화번호는 seed와 테스트 fixture가 쓰지 않는 010-5000 대역이다.
$managerDistricts = [ordered]@{
    1 = @('마포구'); 2 = @('광진구'); 3 = @('용산구'); 4 = @('서초구'); 5 = @('성동구', '동대문구')
    6 = @('중구', '종로구', '성북구', '도봉구'); 7 = @('강동구', '송파구'); 8 = @('동작구', '관악구', '금천구')
    9 = @('구로구', '양천구', '강서구', '영등포구'); 10 = @('강남구', '서대문구')
}
$accounts = foreach ($entry in $managerDistricts.GetEnumerator()) {
    [pscustomobject]@{
        Email = 'facility-{0:D2}@spoit.test' -f $entry.Key
        Nickname = '시설관리자{0:D2}' -f $entry.Key
        Phone = '010-5000-{0:D4}' -f $entry.Key
        Districts = $entry.Value
    }
}
$accountByDistrict = @{}
foreach ($account in $accounts) { foreach ($district in $account.Districts) { $accountByDistrict[$district] = $account } }

# 원본 장소명 → 시설명. 원본만으로는 어느 시설인지 알 수 없거나, 반복·분류 오기가 있는 이름이다.
$nameFixes = [ordered]@{
    '테니스장 A코트' = '어린이대공원 테니스장 A코트'
    '테니스장 B코트' = '어린이대공원 테니스장 B코트'
    '풋살장 3:3' = '어린이대공원 풋살장 3:3'
    '풋살장 5:5' = '어린이대공원 풋살장 5:5'
    '서울숲>서울숲 테니스장 테스트' = '서울숲 테니스장'
    '서울숲>서울숲 배드민턴장2(피클볼, 족구 등)' = '서울숲 배드민턴장2'
    '망원유수지 풋살장>망원유수지 풋살구장' = '망원유수지 풋살장'
    '보라매공원 관리사무소.>인조잔디축구장' = '보라매공원 인조잔디축구장'
    '중랑물재생센터 파크골프장' = '중랑물재생센터 풋살장'
    '노량진 축구장, 야구장' = '노량진 축구장'
    '서울특별시 산악문화체험센터>난지천 공원 풋살장' = '난지천 공원 풋살장'
    '서울특별시 산악문화체험센터>난지천인조잔디축구장' = '난지천 인조잔디 축구장'
}
# 수정 뒤 이름이 같아져도 한 시설로 합치는 곳: "구|시설명" → 좌표를 가져올 원본 장소명.
# 그 밖에 이름이 겹치면 어느 쪽도 넣지 않고 보고한다.
$mergeCoordinatesFrom = @{ '마포구|난지천 인조잔디 축구장' = '난지천 인조잔디 축구장' }

# 원본 시간이 입력 오류이거나, 일부 시간대 상품만 남아 계산 값이 너무 짧은 시설: "구|시설명" → 시작, 종료
$hourFixes = [ordered]@{
    '양천구|서울에너지공사 목동 테니스장 2면' = @('08:00', '19:00')
    '도봉구|다락원체육공원' = @('06:00', '22:00')
    '동대문구|한국외대 인조잔디 구장' = @('06:00', '22:00')
    '서초구|양재근린공원 축구장' = @('06:00', '22:00')
    '서초구|방배배수지체육공원' = @('06:00', '22:00')
}
$defaultHours = @('06:00', '22:00')
$shortHoursMinutes = 6 * 60

function Resolve-RepoPath([string]$path) {
    if ([IO.Path]::IsPathRooted($path)) { return $path }
    return Join-Path $repo $path
}

function ConvertTo-Minutes([string]$value, [bool]$allow24) {
    if ($value -notmatch '^(\d{2}):(\d{2})$') { throw "시간 형식이 HH:mm이 아닙니다: '$value'" }
    $minutes = [int]$Matches[1] * 60 + [int]$Matches[2]
    if ([int]$Matches[2] -ge 60 -or $minutes -gt 24 * 60 -or ($minutes -eq 24 * 60 -and -not $allow24)) {
        throw "시간 값이 범위를 벗어났습니다: '$value'"
    }
    return $minutes
}

function Format-Clock([int]$minutes) {
    return '{0:D2}:{1:D2}' -f [int][math]::Floor($minutes / 60), ($minutes % 60)
}

$script:repeatedNames = [System.Collections.Generic.List[string]]::new()
$script:usedNameFixes = [System.Collections.Generic.HashSet[string]]::new()

function Get-FacilityName([string]$place) {
    if ($nameFixes.Contains($place)) {
        [void]$script:usedNameFixes.Add($place)
        return $nameFixes[$place]
    }
    $words = [System.Collections.Generic.List[string]]::new()
    $previous = @()
    foreach ($part in ($place -split '>')) {
        $current = @($part.Trim() -split '\s+' | Where-Object { $_ })
        # '장소>시설' 이름의 뒤쪽이 앞쪽과 같은 말로 다시 시작하면("서울숲>서울숲 …") 반복을 한 번만 쓴다.
        $same = 0
        while ($same -lt $current.Count -and $same -lt $previous.Count -and $current[$same] -eq $previous[$same]) { $same++ }
        if ($same -gt 0) { $script:repeatedNames.Add($place) }
        foreach ($word in ($current | Select-Object -Skip $same)) { $words.Add($word) }
        $previous = $current
    }
    return (($words -join ' ') -replace '\.+$', '').Trim()
}

function Get-OperatingHours($products) {
    $starts = @($products | Where-Object { -not [string]::IsNullOrWhiteSpace($_.v_min) } |
        ForEach-Object { ConvertTo-Minutes $_.v_min.Trim() $false })
    $ends = @($products | Where-Object { -not [string]::IsNullOrWhiteSpace($_.v_max) } | ForEach-Object {
        $end = ConvertTo-Minutes $_.v_max.Trim() $true
        # 종료가 시작보다 이르면 다음 날 종료다.
        if (-not [string]::IsNullOrWhiteSpace($_.v_min) -and $end -le (ConvertTo-Minutes $_.v_min.Trim() $false)) { $end += 24 * 60 }
        $end
    })
    if ($starts.Count -eq 0 -or $ends.Count -eq 0) { return $null }
    $open = [int][math]::Floor(($starts | Measure-Object -Minimum).Minimum / 60) * 60
    $close = [int][math]::Ceiling(($ends | Measure-Object -Maximum).Maximum / 60) * 60
    return @($open, $close)
}

function ConvertTo-SqlText([string]$value) {
    return "'" + $value.Replace('\', '\\').Replace("'", "''") + "'"
}

function ConvertTo-SqlDecimal([decimal]$value) {
    return $value.ToString('0.0000000', $invariant)
}

function Format-DerivedTable([string[]]$columns, [object[]]$rows) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('(')
    for ($i = 0; $i -lt $rows.Count; $i++) {
        if ($i -eq 0) {
            $cells = for ($c = 0; $c -lt $columns.Count; $c++) { "$($rows[$i][$c]) AS $($columns[$c])" }
            $lines.Add('                 SELECT ' + ($cells -join ', '))
        } else {
            $lines.Add('       UNION ALL SELECT ' + ($rows[$i] -join ', '))
        }
    }
    $lines.Add('       ) v')
    return $lines -join "`n"
}

# ---------------------------------------------------------------------
# 원본 → 장소
# ---------------------------------------------------------------------
$sourcePath = Resolve-RepoPath $InputPath
$data = @((Get-Content -Raw -Encoding utf8 -LiteralPath $sourcePath | ConvertFrom-Json).DATA)
if ($data.Count -eq 0) { throw "원본에 DATA가 없습니다: $sourcePath" }
$mappedRows = @($data | Where-Object { $sportByClass.ContainsKey($_.minclassnm) })

$excluded = [System.Collections.Generic.List[object]]::new()
function Add-Exclusion([string]$district, [string]$place, [string]$reason) {
    $excluded.Add([pscustomobject]@{ District = $district; Place = $place; Reason = $reason })
}

$sourcePlaces = @($data | Group-Object { "$($_.areanm)|$($_.placenm.Trim())" })
$places = foreach ($group in $sourcePlaces) {
    $first = $group.Group[0]
    $district = $first.areanm
    $place = $first.placenm.Trim()
    $products = @($group.Group | Where-Object { $sportByClass.ContainsKey($_.minclassnm) })
    if ($products.Count -eq 0) { Add-Exclusion $district $place '매핑되는 종목 없음'; continue }
    if (-not $districtIds.Contains($district)) { Add-Exclusion $district $place '서울의 구가 아님'; continue }
    $located = @($products | Where-Object { -not [string]::IsNullOrWhiteSpace($_.x) -and -not [string]::IsNullOrWhiteSpace($_.y) })
    if ($located.Count -eq 0) { Add-Exclusion $district $place '좌표 없음'; continue }
    [pscustomobject]@{
        District = $district
        Place = $place
        Name = Get-FacilityName $place
        Products = $products
        # x가 경도, y가 위도다. DECIMAL(10,7)에 맞춰 반올림한다.
        Latitude = [decimal]::Round([decimal]::Parse($located[0].y, $invariant), 7, [MidpointRounding]::AwayFromZero)
        Longitude = [decimal]::Round([decimal]::Parse($located[0].x, $invariant), 7, [MidpointRounding]::AwayFromZero)
    }
}

foreach ($place in $nameFixes.Keys) {
    if (-not $usedNameFixes.Contains($place)) { throw "이름 수정 목록의 장소를 원본에서 찾지 못했습니다: $place" }
}

# ---------------------------------------------------------------------
# 장소 → 시설
# ---------------------------------------------------------------------
$merged = [System.Collections.Generic.List[string]]::new()
$facilities = [System.Collections.Generic.List[object]]::new()
foreach ($group in ($places | Group-Object { "$($_.District)|$($_.Name)" })) {
    $key = $group.Name
    $members = @($group.Group)
    $anchor = $members[0]
    if ($members.Count -gt 1) {
        if (-not $mergeCoordinatesFrom.ContainsKey($key)) {
            foreach ($member in $members) { Add-Exclusion $member.District $member.Place "수정 뒤 같은 구에서 이름이 겹침($($member.Name))" }
            continue
        }
        $anchor = @($members | Where-Object Place -eq $mergeCoordinatesFrom[$key])[0]
        if (-not $anchor) { throw "합칠 시설의 좌표 기준 장소가 없습니다: $key" }
        $merged.Add("$key ← " + (($members.Place) -join ' + '))
    } elseif ($mergeCoordinatesFrom.ContainsKey($key)) {
        throw "합칠 시설로 지정했지만 겹치는 장소가 하나뿐입니다: $key"
    }
    $account = $accountByDistrict[$anchor.District]
    if (-not $account) {
        foreach ($member in $members) { Add-Exclusion $member.District $member.Place '담당 계정이 정해지지 않은 구' }
        continue
    }

    $products = @($members | ForEach-Object { $_.Products })
    $hoursNote = $null
    if ($hourFixes.Contains($key)) {
        $open = ConvertTo-Minutes $hourFixes[$key][0] $false
        $close = ConvertTo-Minutes $hourFixes[$key][1] $true
        $hoursNote = '수정 목록'
    } else {
        $hours = Get-OperatingHours $products
        if ($hours) {
            $open, $close = $hours
        } else {
            $open = ConvertTo-Minutes $defaultHours[0] $false
            $close = ConvertTo-Minutes $defaultHours[1] $true
            $hoursNote = '시간 값 없음'
        }
    }
    if ($close -le $open -or $close - $open -gt 24 * 60) {
        throw "운영 시간을 정할 수 없습니다: $key $(Format-Clock $open)~$(Format-Clock $close)"
    }

    $sportNames = @($products | ForEach-Object { $sportByClass[$_.minclassnm] } | Sort-Object -Unique)
    $address = "서울특별시 $($anchor.District) $($anchor.Name)"
    if ($anchor.Name.Length -gt 100 -or $address.Length -gt 255) { throw "이름 또는 주소가 컬럼 길이를 넘습니다: $address" }
    if ($anchor.Latitude -lt 33 -or $anchor.Latitude -gt 39 -or $anchor.Longitude -lt 124 -or $anchor.Longitude -gt 132) {
        throw "좌표가 국내 범위를 벗어났습니다(x·y가 바뀌었는지 확인): $key"
    }
    $facilities.Add([pscustomobject]@{
        District = $anchor.District
        Name = $anchor.Name
        Places = @($members.Place)
        Sports = @($sports | Where-Object { $sportNames -contains $_.Name } | ForEach-Object Name)
        Latitude = $anchor.Latitude
        Longitude = $anchor.Longitude
        Open = $open
        Close = $close
        HoursNote = $hoursNote
        Account = $account
    })
}
foreach ($key in $hourFixes.Keys) {
    if (-not @($facilities | Where-Object { "$($_.District)|$($_.Name)" -eq $key })) {
        throw "운영 시간 수정 목록의 시설을 찾지 못했습니다: $key"
    }
}
# 출력 순서를 고정해 다시 만들어도 같은 파일이 나오게 한다. 한글 음절은 코드 순서가 가나다순이다.
$facilities.Sort([Comparison[object]] {
    param($a, $b)
    [string]::CompareOrdinal("$($a.District)`t$($a.Name)", "$($b.District)`t$($b.Name)")
})
if ($facilities.Count -eq 0) { throw '등록할 시설이 없습니다.' }

# ---------------------------------------------------------------------
# SQL
# ---------------------------------------------------------------------
$newDistricts = @($districtIds.GetEnumerator() | Where-Object { $seedDistricts -notcontains $_.Key })
$districtTable = Format-DerivedTable @('region_id', 'name') @($newDistricts | ForEach-Object { , @($_.Value, (ConvertTo-SqlText $_.Key)) })
$accountTable = Format-DerivedTable @('email', 'nickname', 'phone') @($accounts | ForEach-Object {
    , @((ConvertTo-SqlText $_.Email), (ConvertTo-SqlText $_.Nickname), (ConvertTo-SqlText $_.Phone)) })
$placeTable = Format-DerivedTable @('gu', 'name', 'latitude', 'longitude', 'open_time', 'close_time', 'end_next_day', 'manager_email') @(
    $facilities | ForEach-Object {
        # 24:00 종료와 자정 넘김은 화면 저장과 같게 다음 날 시각 + end_next_day로 둔다(FacilityScheduleServiceImpl).
        $nextDay = $_.Close -ge 24 * 60
        , @((ConvertTo-SqlText $_.District), (ConvertTo-SqlText $_.Name), (ConvertTo-SqlDecimal $_.Latitude),
            (ConvertTo-SqlDecimal $_.Longitude), (ConvertTo-SqlText ((Format-Clock $_.Open) + ':00')),
            (ConvertTo-SqlText ((Format-Clock ($_.Close % (24 * 60))) + ':00')), $(if ($nextDay) { 'TRUE' } else { 'FALSE' }),
            (ConvertTo-SqlText $_.Account.Email))
    })
$facilitySportRows = @($facilities | ForEach-Object {
    $facility = $_
    $facility.Sports | ForEach-Object { , @((ConvertTo-SqlText $facility.District), (ConvertTo-SqlText $facility.Name), (ConvertTo-SqlText $_)) }
})
$facilitySportTable = Format-DerivedTable @('gu', 'name', 'sport') $facilitySportRows
$defaultTable = (Format-DerivedTable @('sport', 'default_min', 'default_max', 'default_fee', 'default_skill') @($sports | ForEach-Object {
    , @((ConvertTo-SqlText $_.Name), $_.Min, $_.Max, ('{0}.00' -f $_.Fee), (ConvertTo-SqlText $_.Skill)) })) -replace '\) v$', ') d'
$sportNameList = ($sports | ForEach-Object { ConvertTo-SqlText $_.Name }) -join ', '
$sourceRows = $data.Count
$expectedSports = $facilitySportRows.Count
$expectedRules = $facilities.Count * 7

$sql = @"
-- =====================================================================
-- 서울시 공공서비스예약 체육시설 등록
-- 이 파일은 scripts/public-facility-import.ps1이 만든다. 직접 고치지 말고 스크립트의 규칙·수정 목록을 고쳐 다시 만든다.
-- 원본: 서울시 공공서비스예약 체육시설 데이터 ${sourceRows}건(out/public-facilities.json, 커밋하지 않음)
-- 대상: db/seed.sql을 적용한 개발 DB. 새 DB도 seed.sql 다음에 적용한다. spoit_mat_test에는 적용하지 않는다.
-- 내용
--   1. 서울 구 $($newDistricts.Count)개 (seed의 $($seedDistricts.Count)개와 합쳐 $($districtIds.Count)개)
--   2. 시설 관리자 계정 $($accounts.Count)개 ($($accounts[0].Email) ~ $($accounts[-1].Email))
--   3. 시설 $($facilities.Count)곳과 제공 종목 ${expectedSports}행, 요일별 운영 시간 ${expectedRules}행, 담당자 $($facilities.Count)행
-- 원칙
--   * 여러 번 실행해도 결과가 같다. 이미 있는 행은 건너뛴다(구는 이름, 계정은 이메일, 시설은 이름 + 구).
--   * 기존 시설과 facility@spoit.test는 바꾸지 않는다. 이름과 구가 같은 시설이 이미 있으면(논리 삭제 포함) 새로 만들지 않고,
--     주소까지 이 파일과 같은 시설에만 종목·운영 시간·담당자를 붙인다.
--   * 한 트랜잭션이다. 사전 확인에 실패하면 아무 행도 넣지 않는다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 사전 확인. 출력되는 값이 모두 1이어야 진행한다.
--    region_ids_free: 새 구에 쓸 region_id를 다른 이름의 지역이 쓰고 있지 않다.
--    unique_districts: 서울특별시 아래 이름이 같은 구가 둘이면 시설이 두 번 들어가므로 멈춘다.
SELECT (SELECT COUNT(*) FROM app_user WHERE email = 'facility@spoit.test') = 1,
       (SELECT COUNT(*) FROM sport WHERE name IN ($sportNameList)) = $($sports.Count),
       (SELECT COUNT(*) FROM region WHERE region_id = 1 AND parent_region_id IS NULL) = 1,
       NOT EXISTS (SELECT 1 FROM region WHERE parent_region_id = 1 GROUP BY name HAVING COUNT(*) > 1),
       NOT EXISTS (SELECT 1
                     FROM $districtTable
                     JOIN region r ON r.region_id = v.region_id
                    WHERE r.name <> v.name OR NOT (r.parent_region_id <=> 1))
  INTO @public_facility_demo_account, @public_facility_sports, @public_facility_seoul,
       @public_facility_unique_districts, @public_facility_region_ids_free;
SET @public_facility_ready = @public_facility_demo_account AND @public_facility_sports AND @public_facility_seoul
                         AND @public_facility_unique_districts AND @public_facility_region_ids_free;
SELECT @public_facility_demo_account AS demo_account, @public_facility_sports AS sports, @public_facility_seoul AS seoul,
       @public_facility_unique_districts AS unique_districts, @public_facility_region_ids_free AS region_ids_free;
-- 하나라도 0이면 서브쿼리가 두 행을 돌려 "Subquery returns more than 1 row" 오류로 멈춘다.
-- 오류 뒤에도 다음 문장을 이어 실행하는 클라이언트(대화형 SOURCE 등)에 대비해, 아래 INSERT도 모두 @public_facility_ready가 1일 때만 행을 넣는다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @public_facility_ready = 0);

-- 1. 서울 구. seed의 번호가 행정구역 순번 + 4라서(종로구 5 … 송파구 28) 빈 번호를 같은 규칙으로 채운다.
INSERT INTO region (region_id, parent_region_id, name)
SELECT v.region_id, 1, v.name
  FROM $districtTable
  LEFT JOIN region r ON r.parent_region_id = 1 AND r.name = v.name
 WHERE r.region_id IS NULL
   AND @public_facility_ready = 1;

-- 2. 시설 관리자 계정. 비밀번호 해시와 역할은 facility@spoit.test와 같다.
INSERT INTO app_user (email, password_hash, nickname, phone, role)
SELECT v.email, demo.password_hash, v.nickname, v.phone, demo.role
  FROM $accountTable
  JOIN app_user demo ON demo.email = 'facility@spoit.test'
  LEFT JOIN app_user u ON u.email = v.email
 WHERE u.user_id IS NULL
   AND @public_facility_ready = 1;

-- 3. 시설
INSERT INTO facility (region_id, name, address, latitude, longitude, concurrent_match_limit, status, deleted_at)
SELECT r.region_id, v.name, CONCAT('서울특별시 ', v.gu, ' ', v.name), v.latitude, v.longitude, 1, '운영 중', NULL
  FROM $placeTable
  JOIN region r ON r.parent_region_id = 1 AND r.name = v.gu
  LEFT JOIN facility f ON f.region_id = r.region_id AND f.name = v.name
 WHERE f.facility_id IS NULL
   AND @public_facility_ready = 1;

-- 4. 제공 종목과 매칭방 기본 조건. 기본 조건은 db/seed.sql의 같은 종목 시설에서 가장 많이 쓴 조합이다(동률이면 이용료가 낮은 쪽).
INSERT INTO facility_sport (facility_id, sport_id, default_min, default_max, default_fee, default_skill)
SELECT f.facility_id, s.sport_id, d.default_min, d.default_max, d.default_fee, d.default_skill
  FROM $facilitySportTable
  JOIN region r ON r.parent_region_id = 1 AND r.name = v.gu
  JOIN facility f ON f.region_id = r.region_id AND f.name = v.name
                 AND f.address = CONCAT('서울특별시 ', v.gu, ' ', v.name)
  JOIN sport s ON s.name = v.sport
  JOIN $defaultTable ON d.sport = v.sport
  LEFT JOIN facility_sport fs ON fs.facility_id = f.facility_id AND fs.sport_id = s.sport_id
 WHERE fs.facility_id IS NULL
   AND @public_facility_ready = 1;

-- 5. 요일별 운영 시간. 원본 상품의 가장 이른 시작(정시 내림)부터 가장 늦은 종료(정시 올림)까지, 7일 모두 같다.
INSERT INTO operating_rule (facility_id, weekday, open_time, close_time, end_next_day, is_open)
SELECT f.facility_id, w.weekday, v.open_time, v.close_time, v.end_next_day, TRUE
  FROM $placeTable
  JOIN region r ON r.parent_region_id = 1 AND r.name = v.gu
  JOIN facility f ON f.region_id = r.region_id AND f.name = v.name
                 AND f.address = CONCAT('서울특별시 ', v.gu, ' ', v.name)
 CROSS JOIN (SELECT 1 AS weekday UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
             UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) w
  LEFT JOIN operating_rule o ON o.facility_id = f.facility_id AND o.weekday = w.weekday
 WHERE o.operating_rule_id IS NULL
   AND @public_facility_ready = 1;

-- 6. 담당자. 구마다 정한 계정 하나가 맡는다.
INSERT INTO facility_manager (facility_id, user_id)
SELECT f.facility_id, u.user_id
  FROM $placeTable
  JOIN region r ON r.parent_region_id = 1 AND r.name = v.gu
  JOIN facility f ON f.region_id = r.region_id AND f.name = v.name
                 AND f.address = CONCAT('서울특별시 ', v.gu, ' ', v.name)
  JOIN app_user u ON u.email = v.manager_email
  LEFT JOIN facility_manager fm ON fm.facility_id = f.facility_id AND fm.user_id = u.user_id
 WHERE fm.facility_id IS NULL
   AND @public_facility_ready = 1;

COMMIT;

-- 확인 1: places와 facilities가 $($facilities.Count)이고 skipped가 NULL이면 모든 장소가 이 파일의 시설로 들어갔다.
--         skipped에 나온 장소는 이름과 구가 같은 다른 시설이 이미 있어 건너뛴 것이다.
SELECT COUNT(*) AS places,
       COUNT(f.facility_id) AS facilities,
       GROUP_CONCAT(CASE WHEN f.facility_id IS NULL THEN CONCAT(v.gu, ' ', v.name) END SEPARATOR ', ') AS skipped
  FROM $placeTable
  LEFT JOIN region r ON r.parent_region_id = 1 AND r.name = v.gu
  LEFT JOIN facility f ON f.region_id = r.region_id AND f.name = v.name
                      AND f.address = CONCAT('서울특별시 ', v.gu, ' ', v.name);

-- 확인 2: 기대값 seoul_districts $($districtIds.Count), manager_accounts $($accounts.Count), facility_sports $expectedSports, operating_rules $expectedRules, facility_managers $($facilities.Count)
SELECT (SELECT COUNT(*) FROM region WHERE parent_region_id = 1) AS seoul_districts,
       (SELECT COUNT(*) FROM app_user WHERE email LIKE 'facility-__@spoit.test') AS manager_accounts,
       (SELECT COUNT(*) FROM facility_sport WHERE facility_id IN (SELECT fm.facility_id FROM facility_manager fm
                 JOIN app_user u ON u.user_id = fm.user_id WHERE u.email LIKE 'facility-__@spoit.test')) AS facility_sports,
       (SELECT COUNT(*) FROM operating_rule WHERE facility_id IN (SELECT fm.facility_id FROM facility_manager fm
                 JOIN app_user u ON u.user_id = fm.user_id WHERE u.email LIKE 'facility-__@spoit.test')) AS operating_rules,
       (SELECT COUNT(*) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id
         WHERE u.email LIKE 'facility-__@spoit.test') AS facility_managers;
"@

$outputFile = Resolve-RepoPath $OutputPath
[IO.File]::WriteAllText($outputFile, (($sql -replace "`r?`n", "`r`n").TrimEnd() + "`r`n"), [Text.UTF8Encoding]::new($false))

# ---------------------------------------------------------------------
# 검수용 CSV와 요약
# ---------------------------------------------------------------------
$reviewFile = Resolve-RepoPath $ReviewPath
$facilities | ForEach-Object {
    $close = if ($_.Close -eq 24 * 60) { '24:00' } elseif ($_.Close -gt 24 * 60) { "$(Format-Clock ($_.Close - 24 * 60))(다음 날)" } else { Format-Clock $_.Close }
    [pscustomobject][ordered]@{
        '구' = $_.District
        '시설명' = $_.Name
        '원본 placenm' = $_.Places -join ' + '
        '종목' = $_.Sports -join ', '
        '시작' = Format-Clock $_.Open
        '종료' = $close
        '담당 계정' = $_.Account.Email
    }
} | Export-Csv -LiteralPath $reviewFile -Encoding utf8BOM -NoTypeInformation

function Format-List([string]$label, [object[]]$items) {
    if ($items.Count -eq 0) { return "$label 0곳" }
    return "$label $($items.Count)곳: " + ($items -join ', ')
}

Write-Host "원본 ${sourceRows}행 → 종목이 매핑되는 상품 $($mappedRows.Count)행, 장소 $($sourcePlaces.Count)곳"
Write-Host "제외 $($excluded.Count)곳"
foreach ($reason in ($excluded | Group-Object Reason)) {
    Write-Host (Format-List "  $($reason.Name)" @($reason.Group | ForEach-Object { "$($_.District) $($_.Place)" }))
}
foreach ($line in $merged) { Write-Host "합친 시설: $line" }
Write-Host "이름 수정 목록 적용 $($usedNameFixes.Count)곳"
Write-Host (Format-List '반복 줄임 규칙 적용' @($repeatedNames | Sort-Object -Unique))
Write-Host "운영 시간 수정 목록 적용 $(@($facilities | Where-Object HoursNote -eq '수정 목록').Count)곳"
Write-Host (Format-List "시간 값이 없어 $($defaultHours -join '~')로 넣은 시설" @(
    $facilities | Where-Object HoursNote -eq '시간 값 없음' | ForEach-Object { "$($_.District) $($_.Name)" }))
Write-Host (Format-List '수정 목록 밖에서 6시간보다 짧은 시설' @(
    $facilities | Where-Object { -not $_.HoursNote -and $_.Close - $_.Open -lt $shortHoursMinutes } |
        ForEach-Object { "$($_.District) $($_.Name) $(Format-Clock $_.Open)~$(Format-Clock $_.Close)" }))
Write-Host "시설 $($facilities.Count)곳, 제공 종목 ${expectedSports}행, 운영 시간 ${expectedRules}행"
Write-Host ('  종목별: ' + (($sports | ForEach-Object { $name = $_.Name; "$name $(@($facilities | Where-Object { $_.Sports -contains $name }).Count)" }) -join ', '))
Write-Host ('  계정별: ' + (($accounts | ForEach-Object { $email = $_.Email; "$($email.Split('@')[0]) $(@($facilities | Where-Object { $_.Account.Email -eq $email }).Count)" }) -join ', '))
Write-Host ('  새 구: ' + (($newDistricts | ForEach-Object { "$($_.Key) $($_.Value)" }) -join ', '))
Write-Host "SQL: $outputFile"
Write-Host "검수용 CSV: $reviewFile"
