#Requires -Version 7.0
<#
.SYNOPSIS
레이팅 시연용 회원 24명과 10월 경기 이력을 넣는 마이그레이션 SQL을 만든다.
.DESCRIPTION
RatingCalculator·RatingApplyService·MatchResultService가 한 경기를 반영하며 남기는 행을 그대로 만든다.
경기를 시간순으로 따라가며 팀 평균 → 기대 승률 → K → Elo 증감 → 평가 보정을 실제 코드의 자릿수·반올림으로 계산한다.
난수 시드가 고정되어 다시 실행해도 같은 SQL이 나온다. DB에는 접속하지 않는다.
.EXAMPLE
pwsh -File scripts/rating-demo-data.ps1
#>
param(
    [string]$OutputPath = 'db/migrations/20261111-rating-demo-data.sql',
    [string]$ReviewPath = 'out/rating-demo-review.csv',
    [int]$Seed = 42,
    [int[]]$Trace = @()
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$inv = [Globalization.CultureInfo]::InvariantCulture
$rng = [System.Random]::new($Seed)          # 일정·결과·평가
$rng2 = [System.Random]::new($Seed + 1)     # 이름·전화번호·접수 번호처럼 계산에 영향 없는 값
function Rnd([int]$lo, [int]$hi) { return $rng.Next($lo, $hi + 1) }
function Gauss([double]$mean, [double]$sd) {
    $u1 = 1.0 - $rng.NextDouble(); $u2 = $rng.NextDouble()
    return $mean + $sd * [math]::Sqrt(-2.0 * [math]::Log($u1)) * [math]::Cos(2.0 * [math]::PI * $u2)
}
# Java RoundingMode.HALF_UP은 0에서 먼 쪽으로 반올림한다.
function HalfUp([decimal]$v, [int]$scale) { return [math]::Round($v, $scale, [MidpointRounding]::AwayFromZero) }

# ---- RatingCalculator와 같은 계산 ----
function TeamAverage([decimal[]]$mmrs) { $s = [decimal]0; foreach ($m in $mmrs) { $s += $m }; return HalfUp ($s / $mmrs.Count) 2 }
function ExpectedScore([decimal]$own, [decimal]$opp) {
    # Java: BigDecimal.doubleValue() / 400 → Math.pow → BigDecimal.valueOf(double)(= Double.toString) → 4자리 HALF_UP
    $exponent = [double]::Parse(($opp - $own).ToString($inv), $inv) / 400.0
    $value = 1.0 / (1.0 + [math]::Pow(10.0, $exponent))
    return HalfUp ([decimal]::Parse($value.ToString('R', $inv), [Globalization.NumberStyles]::Float, $inv)) 4
}
function KFactor([int]$ratedCount) { if ($ratedCount -lt 5) { 64 } else { 32 } }
function EloDelta([int]$k, [decimal]$actual, [decimal]$expected) { return HalfUp ([decimal]$k * ($actual - $expected)) 2 }
function OverallScore($itemScores) {
    $ws = [decimal]0; $sum = [decimal]0
    foreach ($i in $itemScores) { if ($i.observed) { $ws += $i.w; $sum += $i.w * $i.s } }
    if ($ws -eq 0) { return $null }
    return HalfUp ($sum / $ws) 2
}
function PlayerScore($overalls) { $v = @($overalls | Where-Object { $null -ne $_ }); if ($v.Count -eq 0) { return $null }; $s = [decimal]0; foreach ($x in $v) { $s += $x }; return HalfUp ($s / $v.Count) 2 }
function EvaluationDelta($score) { if ($null -eq $score) { return [decimal]'0.00' }; return HalfUp (($score - 5) * 1) 2 }
# RatingTier.labelOf·TierRank.of와 같은 표시
function TierLabel([decimal]$mmr, [int]$count) {
    if ($count -lt 5) { return "배치 중 ($count/5)" }
    if ($mmr -lt 800) { return '브론즈 4 · 0P' }
    $idx = [math]::Min([int][math]::Floor(($mmr - 800) / 50), 19)
    $tiers = '브론즈', '실버', '골드', '플래티넘', '다이아'
    $into = ($mmr - 800) - 50 * $idx
    return '{0} {1} · {2}P' -f $tiers[[int][math]::Floor($idx / 4)], (4 - $idx % 4), [int][math]::Floor($into * 100 / 50)
}

# ---- 데이터 설계 ----
$sportName = @{ F = '축구+풋살'; T = '테니스' }
$sportLabel = @{ F = '풋살'; T = '테니스' }
$photo = @{ F = '/images/matches/soccer.jpg'; T = '/images/matches/tennis.jpg' }
$itemCodes = @{ F = 'SOCCER_TECH', 'FUTSAL_PASS', 'FUTSAL_POSITION'; T = 'TENNIS_SERVE', 'TENNIS_RALLY', 'TENNIS_POSITION' }
$teamSize = @{ F = 5; T = 2 }
# 20261110-seoul-public-facilities.sql의 시설(구, 이름, 운영 시간). 담당자는 SQL이 이름으로 찾는다.
$facilities = @(
    @{ sport = 'F'; name = '잠실종합운동장 풋살경기장'; gu = '송파구'; open = 7; close = 23 }
    @{ sport = 'F'; name = '서울월드컵경기장 풋살구장'; gu = '마포구'; open = 6; close = 22 }
    @{ sport = 'F'; name = '뚝섬한강공원 축구장 1'; gu = '광진구'; open = 6; close = 22 }
    @{ sport = 'F'; name = '보라매공원 인조잔디축구장'; gu = '동작구'; open = 6; close = 22 }
    @{ sport = 'T'; name = '한남테니스장'; gu = '용산구'; open = 6; close = 22 }
    @{ sport = 'T'; name = '잠원한강테니스장'; gu = '서초구'; open = 8; close = 22 }
    @{ sport = 'T'; name = '월드컵공원 테니스장'; gu = '마포구'; open = 7; close = 21 }
    @{ sport = 'T'; name = '장충테니스장'; gu = '중구'; open = 6; close = 22 }
)
for ($i = 0; $i -lt $facilities.Count; $i++) { $facilities[$i].var = "@rf$($i + 1)" }
$districts = @($facilities.gu | Sort-Object -Unique)
# 회원 번호 → 종목별 경기 수와 숨은 실력(850~1250). 배치 중(1, 2~4경기)과 배치 완료(5~8, 12경기 이상)가 모두 나오게 나눴다.
$plan = @(
    @{ n = 1; F = 15; skill = 1250 }, @{ n = 2; F = 15; skill = 1120 }, @{ n = 3; F = 15; skill = 1000 }, @{ n = 4; F = 15; skill = 900 },
    @{ n = 5; F = 14; skill = 1100 }, @{ n = 6; F = 14; skill = 860 },
    @{ n = 7; F = 11; skill = 1120 }, @{ n = 8; F = 8; skill = 1050 }, @{ n = 9; F = 12; skill = 920 }, @{ n = 10; F = 8; skill = 1110 }, @{ n = 11; F = 8; skill = 980 },
    @{ n = 12; F = 4; skill = 1080 }, @{ n = 13; F = 4; skill = 950 }, @{ n = 14; F = 4; skill = 1120 },
    @{ n = 15; F = 1; skill = 1000 }, @{ n = 16; F = 1; skill = 1080 }, @{ n = 17; F = 1; skill = 880 },
    @{ n = 18; T = 14; skill = 1250 }, @{ n = 19; T = 13; skill = 870 },
    @{ n = 20; T = 13; skill = 1050 }, @{ n = 21; T = 7; skill = 1020 }, @{ n = 22; T = 7; skill = 940 }, @{ n = 23; T = 6; skill = 1080 },
    @{ n = 24; T = 4; skill = 1060 },
    @{ n = 2; T = 4 }, @{ n = 8; T = 6 }, @{ n = 12; T = 1 }, @{ n = 15; T = 1 }
)
# 승패 확률은 팀 실력 평균 차이의 로지스틱(나눗수가 작을수록 실력대로 갈린다), 무승부 10%.
# 평가 항목 점수의 평균은 실력 850이 3점, 1250이 8점이 되게 완만하게 두고 편차로 퍼뜨린다.
$skillDivisor = 30.0; $drawProb = 0.10; $evalShare = 0.8; $evalSlope = 80.0; $evalSd = 1.5
$members = @{}
foreach ($p in $plan) {
    if (-not $members.ContainsKey($p.n)) {
        $members[$p.n] = @{ n = $p.n; email = 'rating-{0:D2}@spoit.test' -f $p.n; skill = $p.skill; sports = [System.Collections.Generic.List[string]]::new()
            need = @{}; mmr = @{}; count = @{}; availableFrom = @{}; lastCalc = @{}; busy = [System.Collections.Generic.List[object]]::new() }
    }
    foreach ($s in 'F', 'T') {
        if ($p.ContainsKey($s)) {
            $m = $members[$p.n]; $m.sports.Add($s); $m.need[$s] = $p[$s]; $m.mmr[$s] = [decimal]'1000.00'; $m.count[$s] = 0; $m.availableFrom[$s] = [datetime]'2026-09-01'
        }
    }
}
$needTotal = @{ F = 0; T = 0 }; foreach ($m in $members.Values) { foreach ($s in $m.need.Keys) { $needTotal[$s] += $m.need[$s] } }
if ($needTotal.F % 10 -or $needTotal.T % 4) { throw "참가 슬롯이 팀 정원에 맞지 않습니다: F=$($needTotal.F) T=$($needTotal.T)" }
$matchesLeft = @{ F = $needTotal.F / 10; T = $needTotal.T / 4 }

# 닉네임: 데모 회원·seed 회원과 겹치지 않는 조합만 쓴다. 전화번호 010-6000 대역은 저장소 어디에도 없다.
$surnames = [char[]]'김이박최정강조윤장임한오서신권황안송류홍'
$given = '민준', '서연', '도윤', '하은', '시우', '지우', '주원', '서윤', '하준', '지민', '예준', '수아', '건우', '지유', '현우', '채원',
    '우진', '다은', '선우', '예린', '연우', '소율', '유준', '하린', '정우', '윤서', '승현', '가은', '준혁', '나연', '지호', '은채',
    '태윤', '서현', '민재', '유나', '은우', '수빈', '재원', '지아'
$taken = [System.Collections.Generic.HashSet[string]]::new()
foreach ($file in 'db/seed.sql', 'db/migrations/20261111-demo-match-data.sql') {
    $text = Get-Content -Raw -Encoding utf8 (Join-Path $repo $file)
    foreach ($match in [regex]::Matches($text, "'([가-힣]{2,4})'(?: AS nickname)?, '010-")) { [void]$taken.Add($match.Groups[1].Value) }
}
foreach ($m in ($members.Values | Sort-Object n)) {
    do { $nick = [string]$surnames[$rng2.Next($surnames.Count)] + $given[$rng2.Next($given.Count)] } while (-not $taken.Add($nick))
    $m.nickname = $nick
    $m.phone = '010-6000-{0:D4}' -f $m.n
    $m.district = $districts[$rng2.Next($districts.Count)]
    $m.createdAt = ([datetime]'2026-09-01').AddDays($rng2.Next(0, 25)).AddHours($rng2.Next(8, 22))
}

# ---- 일정: 10월 하루씩 돌며 경기를 만든다 ----
$october = 0..30 | ForEach-Object { ([datetime]'2026-10-01').AddDays($_) }
$matches = [System.Collections.Generic.List[object]]::new()
$facBusy = @{}
$weekdayKo = '월', '화', '수', '목', '금', '토', '일'
$skills = '초급', '중급', '중급', '고급'
function Overlaps($list, [datetime]$s, [datetime]$e) { foreach ($b in $list) { if ($s -lt $b[1] -and $e -gt $b[0]) { return $true } }; return $false }
function SlotName([int]$h) { if ($h -lt 12) { '오전' } elseif ($h -lt 17) { '오후' } else { '저녁' } }
function TryMatch([string]$sport, [datetime]$day, [int]$hour) {
    if ($matchesLeft[$sport] -le 0) { return $false }
    $start = $day.AddHours($hour); $end = $start.AddHours(2)
    $cands = @($facilities | Where-Object { $_.sport -eq $sport -and $_.open -le $hour -and ($hour + 2) -le $_.close -and -not (Overlaps $facBusy[$_.name] $start $end) })
    if ($cands.Count -eq 0) { return $false }
    $need = $teamSize[$sport] * 2
    # 직전 같은 종목 경기가 반영된 뒤 8시간은 지나야 신청·확정이 들어가고, 다른 경기와 앞뒤 1시간을 비운다.
    $eligible = @($members.Values | Where-Object { $_.need.ContainsKey($sport) -and $_.need[$sport] -gt 0 -and $_.availableFrom[$sport] -le $start.AddHours(-8) -and -not (Overlaps $_.busy $start.AddHours(-1) $end.AddHours(1)) })
    if ($eligible.Count -lt $need) { return $false }
    # 남은 경기를 모두 뛰어야 하는 회원이 못 들어오는 날은 만들지 않는다. 가벼운 회원만으로 채우면 나중에 짝이 모자란다.
    $must = @($members.Values | Where-Object { $_.need.ContainsKey($sport) -and $_.need[$sport] -ge $matchesLeft[$sport] })
    foreach ($m in $must) { if ($eligible -notcontains $m) { return $false } }
    if ($must.Count -gt $need) { throw "종목 $sport 남은 경기 $($matchesLeft[$sport])개에 꼭 들어가야 할 회원이 $($must.Count)명이라 정원을 넘습니다." }
    $chosen = @($eligible | Sort-Object @{ e = { $_.need[$sport] }; Descending = $true }, @{ e = { $rng.NextDouble() } } | Select-Object -First $need)
    $matchesLeft[$sport]--
    $fac = $cands[$rng.Next($cands.Count)]
    if (-not $facBusy.ContainsKey($fac.name)) { $facBusy[$fac.name] = [System.Collections.Generic.List[object]]::new() }
    $facBusy[$fac.name].Add(@($start, $end))
    $parts = foreach ($m in $chosen) {
        $m.need[$sport]--; $m.busy.Add(@($start.AddHours(-1), $end.AddHours(1)))
        # 신청은 직전 경기 반영 뒤에, 확정(entry_mmr 스냅샷)은 신청 1~3시간 뒤에, 모두 배정(시작 1시간 전)보다 앞선다.
        $lead = $start.AddDays(-(Rnd 2 5)).AddHours(-(Rnd 0 10))
        $created = if ($lead -gt $m.availableFrom[$sport]) { $lead } else { $m.availableFrom[$sport] }
        if ($created -gt $start.AddHours(-6)) { $created = $start.AddHours(-6) }
        $confirmed = $created.AddMinutes((Rnd 60 180))
        @{ m = $m; entry = $m.mmr[$sport]; created = $created; confirmed = $confirmed; side = $null; pid = 0 }
    }
    $parts = @($parts | Sort-Object { $_.confirmed })
    for ($i = 0; $i -lt $parts.Count; $i++) { $parts[$i].pid = $i + 1 }     # 참가자 번호는 확정 순서
    # 관리자의 수동 배정: 무작위로 반씩 나눈다. MMR로 균형을 맞추면 강한 회원의 팀이 늘 약한 회원과 묶여 승률이 0.5로 수렴해
    # 티어가 갈리지 않는다.
    $shuffled = @($parts | Sort-Object { $rng.NextDouble() })
    for ($i = 0; $i -lt $shuffled.Count; $i++) { $shuffled[$i].side = if ($i -lt $teamSize[$sport]) { 'A' } else { 'B' } }
    $match = @{ n = $matches.Count + 1; sport = $sport; fac = $fac; start = $start; end = $end; parts = $parts; assignedAt = $start.AddHours(-1) }
    Simulate $match
    $matches.Add($match)
    return $true
}
function Simulate($match) {
    $sport = $match.sport; $parts = $match.parts
    $a = @($parts | Where-Object side -eq 'A'); $b = @($parts | Where-Object side -eq 'B')
    # TeamCaptainSelector: entry_mmr 최대, 같으면 participant_id 작은 쪽
    $capA = ($a | Sort-Object @{ e = { $_.entry }; Descending = $true }, { $_.pid })[0]
    $capB = ($b | Sort-Object @{ e = { $_.entry }; Descending = $true }, { $_.pid })[0]
    $match.capA = $capA; $match.capB = $capB
    $skA = ($a | ForEach-Object { $_.m.skill } | Measure-Object -Average).Average
    $skB = ($b | ForEach-Object { $_.m.skill } | Measure-Object -Average).Average
    $pA = 1.0 / (1.0 + [math]::Pow(10.0, ($skB - $skA) / $skillDivisor))
    $r = $rng.NextDouble()
    $match.result = if ($r -lt $drawProb) { 'DRAW' } elseif (($r - $drawProb) / (1 - $drawProb) -lt $pA) { 'A_WIN' } else { 'B_WIN' }
    $end = $match.end
    $match.subA = $end.AddMinutes((Rnd 20 90)); $match.subB = $end.AddMinutes((Rnd 30 180))
    $match.resultConfirmedAt = if ($match.subA -gt $match.subB) { $match.subA } else { $match.subB }
    $match.viaEvaluation = $rng.NextDouble() -lt $evalShare
    $evalScores = @{}
    $match.evaluations = [System.Collections.Generic.List[object]]::new()
    if ($match.viaEvaluation) {
        $match.evalA = $match.subA.AddMinutes((Rnd 10 60)); $match.evalB = $match.subB.AddMinutes((Rnd 10 60))
        foreach ($cap in $capA, $capB) {
            foreach ($t in $parts) {
                if ($t.pid -eq $cap.pid) { continue }
                $base = 3 + ($t.m.skill - 850) / $evalSlope
                $items = @(foreach ($i in 1..3) {
                    @{ w = [decimal]'1.00'; s = [math]::Max(0, [math]::Min(10, [int][math]::Round((Gauss $base $evalSd)))); observed = ($rng.NextDouble() -ge 0.04) }
                })
                $overall = OverallScore $items
                $match.evaluations.Add(@{ evaluator = $cap; target = $t; items = $items; overall = $overall; at = $(if ($cap -eq $capA) { $match.evalA } else { $match.evalB }) })
                if (-not $evalScores.ContainsKey($t.pid)) { $evalScores[$t.pid] = [System.Collections.Generic.List[object]]::new() }
                $evalScores[$t.pid].Add($overall)
            }
        }
        # 결과 확정과 두 팀장 평가 중 마지막 제출이 반영한다(MatchResultService.applyRatingIfReady).
        $calc = $match.resultConfirmedAt; foreach ($x in $match.evalA, $match.evalB) { if ($x -gt $calc) { $calc = $x } }
        $match.calculatedAt = $calc
    } else {
        # 평가 없이 마감 처리: 매분 0초에 도는 스케줄러가 종료 24시간 뒤 첫 실행에서 반영한다(closeResult).
        $match.calculatedAt = $end.AddHours(24)
    }
    $avgA = TeamAverage @($a | ForEach-Object { $_.entry }); $avgB = TeamAverage @($b | ForEach-Object { $_.entry })
    $expA = ExpectedScore $avgA $avgB; $expB = ExpectedScore $avgB $avgA
    $match.avgA = $avgA; $match.avgB = $avgB
    $match.history = [System.Collections.Generic.List[object]]::new()
    foreach ($p in ($parts | Sort-Object { $_.m.n })) {            # RatingApplyService는 user_id 오름차순
        $exp = if ($p.side -eq 'A') { $expA } else { $expB }
        $actual = switch ($match.result) { 'A_WIN' { if ($p.side -eq 'A') { [decimal]'1.0' } else { [decimal]'0.0' } } 'B_WIN' { if ($p.side -eq 'B') { [decimal]'1.0' } else { [decimal]'0.0' } } default { [decimal]'0.5' } }
        $k = KFactor $p.m.count[$sport]
        $elo = EloDelta $k $actual $exp
        $score = if ($evalScores.ContainsKey($p.pid)) { PlayerScore $evalScores[$p.pid] } else { $null }
        $evd = EvaluationDelta $score
        $before = $p.m.mmr[$sport]; $after = $before + $elo + $evd
        $match.history.Add(@{ p = $p; before = $before; after = $after; elo = $elo; evd = $evd; final = $elo + $evd; score = $score; actual = $actual; expected = $exp })
        $p.m.mmr[$sport] = $after; $p.m.count[$sport]++; $p.m.lastCalc[$sport] = $match.calculatedAt
        $p.m.availableFrom[$sport] = $match.calculatedAt.AddHours((Rnd 1 6))
    }
}

$weekdayHours = 18, 19, 20; $weekendHours = 9, 10, 11, 13, 14, 15, 16
foreach ($day in $october) {
    $weekend = $day.DayOfWeek -in 'Saturday', 'Sunday'
    $hours = if ($weekend) { $weekendHours } else { $weekdayHours }
    foreach ($sport in 'F', 'T') {
        $perDay = if ($sport -eq 'T') { if ($weekend) { 3 } else { 2 } } else { if ($weekend) { 2 } else { 1 } }
        $made = 0
        foreach ($h in ($hours | Sort-Object { $rng.NextDouble() })) {
            if ($made -ge $perDay) { break }
            if (TryMatch $sport $day $h) { $made++ }
        }
    }
}
$left = @($members.Values | Where-Object { ($_.need.Values | Measure-Object -Sum).Sum -gt 0 })
if ($left.Count) { throw '10월 안에 일정을 다 잡지 못했습니다: ' + (($left | ForEach-Object { "회원$($_.n) F=$($_.need.F) T=$($_.need.T)" }) -join ', ') }

# ---- 분포 조건 확인 ----
$cards = foreach ($m in ($members.Values | Sort-Object n)) { foreach ($s in $m.sports) { [pscustomobject]@{ n = $m.n; sport = $s; count = $m.count[$s]; mmr = $m.mmr[$s]; tier = TierLabel $m.mmr[$s] $m.count[$s] } } }
$placed = @($cards | Where-Object count -ge 5)
$checks = [ordered]@{
    '1경기 회원 3명 이상' = @($cards | Where-Object count -eq 1 | Select-Object -Unique n).Count -ge 3
    '2~4경기 회원 3명 이상' = @($cards | Where-Object { $_.count -ge 2 -and $_.count -le 4 } | Select-Object -Unique n).Count -ge 3
    '5~8경기 회원 4명 이상' = @($cards | Where-Object { $_.count -ge 5 -and $_.count -le 8 } | Select-Object -Unique n).Count -ge 4
    '12경기 이상 회원 4명 이상' = @($cards | Where-Object count -ge 12 | Select-Object -Unique n).Count -ge 4
    '브론즈 2명 이상' = @($placed | Where-Object tier -like '브론즈*' | Select-Object -Unique n).Count -ge 2
    '실버 2명 이상' = @($placed | Where-Object tier -like '실버*' | Select-Object -Unique n).Count -ge 2
    '골드 2명 이상' = @($placed | Where-Object tier -like '골드*' | Select-Object -Unique n).Count -ge 2
    '두 종목 회원 4명 이상' = @($cards | Group-Object n | Where-Object Count -eq 2).Count -ge 4
    '승·패·무 모두 등장' = @($matches.result | Sort-Object -Unique).Count -eq 3
}
# 이력에 남는 경기별 평가 점수(evaluation_score)가 양 끝에 몰리지 않고, 한 회원이 매번 같은 점수를 받지 않게 한다.
$evaluated = @(foreach ($mt in $matches) { foreach ($h in $mt.history) { if ($null -ne $h.score) { [pscustomobject]@{ n = $h.p.m.n; score = $h.score } } } })
$scoreCount = [math]::Max(1, $evaluated.Count)
$checks['평가 점수 1점 미만 5% 이하'] = @($evaluated | Where-Object score -lt 1).Count / $scoreCount -le 0.05
$checks['평가 점수 9.5점 이상 5% 이하'] = @($evaluated | Where-Object score -ge 9.5).Count / $scoreCount -le 0.05
$checks['평가 점수 4~6점 30% 이상'] = @($evaluated | Where-Object { $_.score -ge 4 -and $_.score -le 6 }).Count / $scoreCount -ge 0.30
# 평가를 한 번만 받은 회원은 비교할 값이 없어 두 번 이상 받은 회원만 본다.
$checks['한 회원의 평가 점수가 모두 같지 않음'] = -not ($evaluated | Group-Object n | Where-Object { $_.Count -ge 2 -and @($_.Group.score | Sort-Object -Unique).Count -eq 1 })
$failed = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object Key)
if ($failed.Count) { throw "분포 조건을 만족하지 못했습니다(시드 $Seed): $($failed -join ', ')" }

# ---- SQL ----
function Lit($v) {
    if ($null -eq $v) { return 'NULL' }
    if ($v -is [bool]) { return $(if ($v) { 'TRUE' } else { 'FALSE' }) }
    if ($v -is [int] -or $v -is [long]) { return [string]$v }
    if ($v -is [decimal]) { return $v.ToString($inv) }
    if ($v -is [datetime]) { return "'" + $v.ToString('yyyy-MM-dd HH:mm:ss', $inv) + "'" }
    if ($v -is [hashtable] -and $v.ContainsKey('raw')) { return $v.raw }
    return "'" + ([string]$v).Replace('\', '\\').Replace("'", "''") + "'"
}
function Ref([string]$var, [int]$n) { return @{ raw = "($var + $n)" } }
function Raw([string]$s) { return @{ raw = $s } }
$out = [System.Collections.Generic.List[string]]::new()
function Insert([string]$table, [string[]]$cols, [object[]]$rows, [int]$chunk = 150) {
    for ($i = 0; $i -lt $rows.Count; $i += $chunk) {
        $lines = [System.Collections.Generic.List[string]]::new()
        $part = $rows[$i..([math]::Min($i + $chunk, $rows.Count) - 1)]
        for ($r = 0; $r -lt $part.Count; $r++) {
            $vals = @($part[$r] | ForEach-Object { Lit $_ })
            if ($r -eq 0) { $vals = @(for ($c = 0; $c -lt $cols.Count; $c++) { "$($vals[$c]) AS $($cols[$c])" }) }
            $lines.Add($(if ($r -eq 0) { '                 SELECT ' } else { '       UNION ALL SELECT ' }) + ($vals -join ', '))
        }
        $out.Add("INSERT INTO $table ($($cols -join ', '))`nSELECT * FROM (`n$($lines -join "`n")`n) v`n WHERE @rating_ready = 1;`n")
    }
}
$receiptChars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
$receipts = [System.Collections.Generic.HashSet[string]]::new()
function Receipt([datetime]$at) {
    do { $r = 'RCPT-' + $at.ToString('yyyyMMdd', $inv) + '-' + (-join (1..8 | ForEach-Object { $receiptChars[$rng2.Next($receiptChars.Length)] })) } while (-not $receipts.Add($r))
    return $r
}

$memberList = @($members.Values | Sort-Object n)
$matchList = @($matches | Sort-Object start, n)
for ($i = 0; $i -lt $matchList.Count; $i++) { $matchList[$i].n = $i + 1 }
$apps = [System.Collections.Generic.List[object]]::new()   # 신청 = 참가자 (번호 같음)
foreach ($mt in $matchList) { foreach ($p in $mt.parts) { $apps.Add(@{ n = $apps.Count + 1; match = $mt; p = $p }); $p.gid = $apps.Count } }
$evals = [System.Collections.Generic.List[object]]::new()
foreach ($mt in $matchList) { foreach ($ev in $mt.evaluations) { $evals.Add(@{ n = $evals.Count + 1; match = $mt; ev = $ev }) } }
$historyRows = @(foreach ($mt in $matchList) { foreach ($h in $mt.history) { @{ match = $mt; h = $h } } })

$out.Add(@"
-- =====================================================================
-- 레이팅 시연 데이터: 회원 $($memberList.Count)명, 2026년 10월 경기 $($matchList.Count)개(축구+풋살 $(@($matchList | Where-Object sport -eq 'F').Count), 테니스 $(@($matchList | Where-Object sport -eq 'T').Count))와 레이팅 이력 $($historyRows.Count)행
-- 이 파일은 scripts/rating-demo-data.ps1이 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261109-evaluation-items.sql, 20261110-seoul-public-facilities.sql, 20261111-demo-match-data.sql을 적용한 DB (로컬·운영)
-- 내용
--   * 회원 rating-01~24@spoit.test (비밀번호·역할은 demo-001@spoit.test와 같음). 마이페이지에서 배치 중과 배치 완료가 모두 보인다.
--   * 경기는 공공 체육시설에서 수동 배정(담당자)·결과 확정·레이팅 반영까지 끝난 종료 상태다.
--     $(@($matchList | Where-Object viaEvaluation).Count)개는 두 팀장의 평가가 들어온 시점에, $(@($matchList | Where-Object { -not $_.viaEvaluation }).Count)개는 평가 없이 결과 마감(종료 24시간 뒤)에 반영됐다.
--   * 이력은 RatingCalculator와 같은 식(팀 평균 → 기대 승률 → K 64/32 → Elo → 평가 보정)으로 1000부터 시간순으로 계산했다.
-- 원칙
--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.
--   * rating-01@spoit.test가 이미 있으면 적용된 것으로 보고 아무것도 넣지 않는다(오류 없음).
--   * 시설·종목·평가 항목·담당자는 이름으로 찾는다. 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다. 기존 행은 바꾸지 않는다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- 0. 이름으로 번호 찾기
"@)
foreach ($s in 'F', 'T') { $out.Add("SET @rs_$s = (SELECT MAX(sport_id) FROM sport WHERE name = $(Lit $sportName[$s]));") }
foreach ($f in $facilities) {
    $out.Add("SET $($f.var) = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 WHERE r.name = $(Lit $f.gu) AND f.name = $(Lit $f.name) AND f.deleted_at IS NULL);")
    $out.Add("SET $($f.var)_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id WHERE fm.facility_id = $($f.var) AND u.email LIKE 'facility-__@spoit.test');")
}
foreach ($d in $districts) { $out.Add("SET @rr_$d = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = $(Lit $d));") }
foreach ($s in 'F', 'T') { for ($i = 0; $i -lt 3; $i++) { $out.Add("SET @ri_${s}_$i = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = $(Lit $itemCodes[$s][$i]) AND is_active = TRUE AND sport_id = @rs_$s);") } }
$out.Add("SELECT MAX(password_hash), MAX(role) INTO @rating_hash, @rating_role FROM app_user WHERE email = 'demo-001@spoit.test';")
$facChecks = ($facilities | ForEach-Object { "($($_.var) IS NOT NULL AND $($_.var)_mgr IS NOT NULL)" }) -join ' + '
$itemChecks = @(foreach ($s in 'F', 'T') { 0..2 | ForEach-Object { "(@ri_${s}_$_ IS NOT NULL)" } }) -join ' + '
$regionChecks = ($districts | ForEach-Object { "(@rr_$_ IS NOT NULL)" }) -join ' + '
$receiptList = ($apps | ForEach-Object { $_.receipt = Receipt $_.p.created; Lit $_.receipt }) -join ', '
$out.Add(@"

-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT (@rs_F IS NOT NULL AND @rs_T IS NOT NULL), ($facChecks) = $($facilities.Count), ($regionChecks) = $($districts.Count), ($itemChecks) = 6,
       @rating_hash IS NOT NULL,
       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN ($receiptList)),
       EXISTS (SELECT 1 FROM app_user WHERE email = 'rating-01@spoit.test')
  INTO @rating_sports, @rating_facilities, @rating_regions, @rating_items, @rating_demo_account, @rating_receipts_free, @rating_already_applied;
SET @rating_checks = @rating_sports AND @rating_facilities AND @rating_regions AND @rating_items AND @rating_demo_account AND @rating_receipts_free;
SET @rating_ready = @rating_checks AND NOT @rating_already_applied;
SELECT @rating_sports AS sports, @rating_facilities AS facilities, @rating_regions AS districts, @rating_items AS evaluation_items,
       @rating_demo_account AS demo_account, @rating_receipts_free AS receipts_free, @rating_already_applied AS already_applied;
-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @rating_checks = 0 AND @rating_already_applied = 0);

SELECT MAX(user_id), (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),
       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant), (SELECT COALESCE(MAX(evaluation_id), 0) FROM match_evaluation)
  INTO @ru, @rm, @ra, @rp, @re
  FROM app_user;

-- 1. 회원
"@)
Insert 'app_user' @('user_id', 'email', 'password_hash', 'nickname', 'phone', 'role', 'created_at') @($memberList | ForEach-Object { , @((Ref '@ru' $_.n), $_.email, (Raw '@rating_hash'), $_.nickname, $_.phone, (Raw '@rating_role'), $_.createdAt) })
Insert 'user_region' @('user_id', 'region_id') @($memberList | ForEach-Object { , @((Ref '@ru' $_.n), (Raw "@rr_$($_.district)")) })
Insert 'user_preferred_sport' @('user_id', 'sport_id') @($memberList | ForEach-Object { , @((Ref '@ru' $_.n), (Raw "@rs_$($_.sports[0])")) })
$out.Add('-- 종목별 레이팅은 마지막 이력과 같은 값이다(current_mmr = 마지막 mmr_after, rated_match_count = 이력 행 수).')
Insert 'sport_rating' @('user_id', 'sport_id', 'current_mmr', 'rated_match_count', 'updated_at') @(foreach ($m in $memberList) { foreach ($s in $m.sports) { , @((Ref '@ru' $m.n), (Raw "@rs_$s"), $m.mmr[$s], $m.count[$s], $m.lastCalc[$s]) } })

$out.Add('-- 2. 경기 (팀장은 참가자를 넣은 뒤 지정한다)')
$matchCols = 'match_id', 'facility_id', 'sport_id', 'created_by', 'title', 'start_at', 'end_at', 'min_participants', 'max_participants', 'team_a_capacity', 'team_b_capacity',
    'fee_per_person', 'required_skill', 'status', 'photo_url', 'assignment_method', 'assigned_by', 'assignment_confirmed_at', 'result_status', 'final_result', 'result_confirmed_at',
    'result_reviewed_by', 'rating_status', 'rating_processed_at'
Insert 'social_match' $matchCols @(foreach ($mt in $matchList) {
    $gu = if ($mt.fac.gu.Length -eq 2) { $mt.fac.gu } else { $mt.fac.gu.Substring(0, $mt.fac.gu.Length - 1) }
    $title = '{0} {1}요일 {2} {3} {4}:{4}' -f $gu, $weekdayKo[([int]$mt.start.DayOfWeek + 6) % 7], (SlotName $mt.start.Hour), $sportLabel[$mt.sport], $teamSize[$mt.sport]
    , @((Ref '@rm' $mt.n), (Raw $mt.fac.var), (Raw "@rs_$($mt.sport)"), (Raw "$($mt.fac.var)_mgr"), $title, $mt.start, $mt.end, ($teamSize[$mt.sport] * 2), ($teamSize[$mt.sport] * 2),
        $teamSize[$mt.sport], $teamSize[$mt.sport], [decimal]0, $skills[$rng2.Next($skills.Count)], '종료', $photo[$mt.sport], 'MANUAL', (Raw "$($mt.fac.var)_mgr"), $mt.assignedAt,
        'CONFIRMED', $mt.result, $mt.resultConfirmedAt, $null, 'APPLIED', $mt.calculatedAt)
})

$out.Add('-- 3. 신청·확정 참가자 (entry_mmr은 확정 시점의 종목 레이팅)')
Insert 'match_application' @('application_id', 'match_id', 'applicant_user_id', 'club_id', 'application_type', 'status', 'receipt_no', 'created_at', 'confirmed_at') @($apps | ForEach-Object {
    , @((Ref '@ra' $_.n), (Ref '@rm' $_.match.n), (Ref '@ru' $_.p.m.n), $null, '개인', '확정', $_.receipt, $_.p.created, $_.p.confirmed) })
Insert 'match_participant' @('participant_id', 'application_id', 'match_id', 'user_id', 'participation_status', 'match_side', 'entry_mmr', 'joined_at') @($apps | ForEach-Object {
    , @((Ref '@rp' $_.n), (Ref '@ra' $_.n), (Ref '@rm' $_.match.n), (Ref '@ru' $_.p.m.n), '확정', $_.p.side, $_.p.entry, $_.p.confirmed) })
foreach ($mt in $matchList) {
    $out.Add("UPDATE social_match SET captain_a_participant_id = (@rp + $($mt.capA.gid)), captain_b_participant_id = (@rp + $($mt.capB.gid)) WHERE @rating_ready = 1 AND match_id = (@rm + $($mt.n));")
}

$out.Add("`n-- 4. 결과 제출 (두 팀장이 같은 결과를 내 확정)")
Insert 'match_result_submission' @('match_id', 'captain_participant_id', 'reported_result', 'submission_status', 'submitted_at') @(foreach ($mt in $matchList) {
    , @((Ref '@rm' $mt.n), (Ref '@rp' $mt.capA.gid), $mt.result, 'CONFIRMED', $mt.subA)
    , @((Ref '@rm' $mt.n), (Ref '@rp' $mt.capB.gid), $mt.result, 'CONFIRMED', $mt.subB)
})

$out.Add('-- 5. 평가 (팀장이 자신을 뺀 양 팀 전원을 평가. overall_score는 관찰한 항목의 가중 평균)')
Insert 'match_evaluation' @('evaluation_id', 'match_id', 'sport_id', 'evaluator_participant_id', 'target_participant_id', 'overall_score', 'scale_max', 'status', 'submitted_at') @($evals | ForEach-Object {
    , @((Ref '@re' $_.n), (Ref '@rm' $_.match.n), (Raw "@rs_$($_.match.sport)"), (Ref '@rp' $_.ev.evaluator.gid), (Ref '@rp' $_.ev.target.gid), $_.ev.overall, 10, '제출 완료', $_.ev.at) })
Insert 'match_evaluation_score' @('evaluation_id', 'item_id', 'sport_id', 'score', 'observation_status') @(foreach ($e in $evals) { for ($i = 0; $i -lt 3; $i++) {
    $item = $e.ev.items[$i]
    , @((Ref '@re' $e.n), (Raw "@ri_$($e.match.sport)_$i"), (Raw "@rs_$($e.match.sport)"), $(if ($item.observed) { [int]$item.s } else { $null }), $(if ($item.observed) { 'OBSERVED' } else { 'NOT_OBSERVED' }))
} })

$out.Add('-- 6. 레이팅 이력 (반영 시각 순)')
Insert 'sport_rating_history' @('match_id', 'participant_id', 'user_id', 'sport_id', 'mmr_before', 'mmr_after', 'elo_delta', 'evaluation_delta', 'final_delta', 'evaluation_score',
    'actual_score', 'expected_score', 'team_avg_mmr_a', 'team_avg_mmr_b', 'algorithm_version', 'calculated_at') @(foreach ($row in ($historyRows | Sort-Object { $_.match.calculatedAt }, { $_.match.n }, { $_.h.p.m.n })) {
    $h = $row.h; $mt = $row.match
    , @((Ref '@rm' $mt.n), (Ref '@rp' $h.p.gid), (Ref '@ru' $h.p.m.n), (Raw "@rs_$($mt.sport)"), $h.before, $h.after, $h.elo, $h.evd, $h.final, $h.score, $h.actual, $h.expected, $mt.avgA, $mt.avgB, 'ELO-K32-V2', $mt.calculatedAt)
})

$placedUsers = @($placed | Select-Object -Unique n).Count
$out.Add(@"
COMMIT;

-- 확인: 이 파일로 넣은 행 수 (이미 적용된 DB에서도 같은 값이 나와야 한다)
SELECT (SELECT COUNT(*) FROM app_user WHERE email LIKE 'rating-__@spoit.test') AS rating_users,
       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE u.email LIKE 'rating-__@spoit.test') AS history_rows,
       (SELECT COUNT(DISTINCT h.match_id) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE u.email LIKE 'rating-__@spoit.test') AS rated_matches,
       (SELECT COUNT(*) FROM sport_rating sr JOIN app_user u ON u.user_id = sr.user_id WHERE u.email LIKE 'rating-__@spoit.test' AND sr.rated_match_count >= 5) AS placed_cards,
       (SELECT COUNT(*) FROM sport_rating sr JOIN app_user u ON u.user_id = sr.user_id WHERE u.email LIKE 'rating-__@spoit.test' AND sr.rated_match_count < 5) AS placing_cards;
-- 기대값: rating_users $($memberList.Count), history_rows $($historyRows.Count), rated_matches $($matchList.Count), placed_cards $($placed.Count), placing_cards $($cards.Count - $placed.Count)
"@)
$outputFile = if ([IO.Path]::IsPathRooted($OutputPath)) { $OutputPath } else { Join-Path $repo $OutputPath }
[IO.File]::WriteAllText($outputFile, ((($out -join "`n") -replace "`r?`n", "`r`n").TrimEnd() + "`r`n"), [Text.UTF8Encoding]::new($false))

# ---- 검수용 CSV와 요약 ----
$reviewFile = if ([IO.Path]::IsPathRooted($ReviewPath)) { $ReviewPath } else { Join-Path $repo $ReviewPath }
$reviewDir = Split-Path -Parent $reviewFile
if (-not (Test-Path $reviewDir)) { New-Item -ItemType Directory $reviewDir | Out-Null }
$cards | ForEach-Object { $m = $members[$_.n]; [pscustomobject][ordered]@{ '회원' = $m.email; '닉네임' = $m.nickname; '종목' = $sportName[$_.sport]; '경기 수' = $_.count; '최종 MMR' = $_.mmr.ToString('0.00', $inv);'티어 표시' = $_.tier; '배치 상태' = $(if ($_.count -ge 5) { '배치 완료' } else { '배치 중' }) } } |
    Export-Csv -LiteralPath $reviewFile -Encoding utf8BOM -NoTypeInformation

Write-Host "경기 $($matchList.Count)개 (축구+풋살 $(@($matchList | Where-Object sport -eq 'F').Count), 테니스 $(@($matchList | Where-Object sport -eq 'T').Count)); 평가 반영 $(@($matchList | Where-Object viaEvaluation).Count), 마감 반영 $(@($matchList | Where-Object { -not $_.viaEvaluation }).Count)"
Write-Host ('결과: ' + (($matchList | Group-Object result | Sort-Object Name | ForEach-Object { "$($_.Name) $($_.Count)" }) -join ', '))
Write-Host "참가 $($apps.Count), 이력 $($historyRows.Count), 평가 $($evals.Count), 평가 점수 $($evals.Count * 3), 결과 제출 $($matchList.Count * 2)"
Write-Host "카드 $($cards.Count)장: 1경기 $(@($cards | Where-Object count -eq 1).Count), 2~4경기 $(@($cards | Where-Object { $_.count -ge 2 -and $_.count -le 4 }).Count), 5~8경기 $(@($cards | Where-Object { $_.count -ge 5 -and $_.count -le 8 }).Count), 12경기 이상 $(@($cards | Where-Object count -ge 12).Count); 두 종목 회원 $(@($cards | Group-Object n | Where-Object Count -eq 2).Count)명"
Write-Host "배치 완료 $($placed.Count)장(${placedUsers}명): 브론즈 $(@($placed | Where-Object tier -like '브론즈*').Count), 실버 $(@($placed | Where-Object tier -like '실버*').Count), 골드 $(@($placed | Where-Object tier -like '골드*').Count); MMR $(($cards.mmr | Measure-Object -Minimum).Minimum)~$(($cards.mmr | Measure-Object -Maximum).Maximum)"
$bins = [ordered]@{ '1 미만' = { $_ -lt 1 }; '1~3 미만' = { $_ -ge 1 -and $_ -lt 3 }; '3~4 미만' = { $_ -ge 3 -and $_ -lt 4 }; '4~6' = { $_ -ge 4 -and $_ -le 6 }
    '6 초과~8 미만' = { $_ -gt 6 -and $_ -lt 8 }; '8~9.5 미만' = { $_ -ge 8 -and $_ -lt 9.5 }; '9.5 이상' = { $_ -ge 9.5 } }
Write-Host ("평가 점수 $($evaluated.Count)건: " + (($bins.GetEnumerator() | ForEach-Object { $f = $_.Value; "$($_.Key) $(@($evaluated.score | Where-Object $f).Count)" }) -join ', '))
Write-Host "SQL: $outputFile"
Write-Host "검수용 CSV: $reviewFile"
foreach ($n in $Trace) {
    foreach ($mt in $matchList) {
        foreach ($h in $mt.history) {
            if ($h.p.m.n -ne $n) { continue }
            $mates = ($mt.parts | Where-Object { $_.side -eq $h.p.side -and $_.m.n -ne $n } | ForEach-Object { $_.m.skill }) -join '/'
            $opps = ($mt.parts | Where-Object { $_.side -ne $h.p.side } | ForEach-Object { $_.m.skill }) -join '/'
            Write-Host ('회원{0} {1} {2:MM-dd} {3} {4} 팀[{5}] 상대[{6}] 기대 {7} Elo {8} 평가 {9} → {10}' -f $n, $mt.sport, $mt.start, $h.p.side, $mt.result, $mates, $opps, $h.expected, $h.elo, $h.evd, $h.after)
        }
    }
}
