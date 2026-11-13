# -*- coding: utf-8 -*-
"""동호회 시연 데이터 SQL 생성기.

대상: 20261110-seoul-public-facilities.sql과 20261112-club-single-region.sql까지 적용한 DB.
출력: db/migrations/20261112-club-demo-data.sql (한 트랜잭션 SQL, UTF-8·LF)
      db/migrations/20261113-club-existing-data.sql (기존 동호회 데이터, 아래 EX_ 부분. 시연 데이터 다음에 적용)
실행: 저장소 루트에서 python scripts/club-demo-data.py (Python 3.8 이상)
같은 시드(RNG)로 만들므로 다시 실행해도 같은 파일이 나온다.

넣는 것
  * 축구+풋살 동호회 10개(시연 2 + 다른 동호회 8): 이름·소개·활동 시간·인원이 실제 동호회처럼 다르다.
  * 2026년 8월~11월 초 동호회끼리 치른 5:5 경기. 경기마다 팀 신청(확정)·참가자·결과 제출·레이팅 이력이 있고,
    평가 없이 결과 마감(종료 24시간 뒤)에 레이팅이 반영된 종료 상태다.
    레이팅은 RatingCalculator와 같은 식(팀 평균 → 기대 승률 → K 64/32 → Elo, 평가 보정 0)으로 1000부터 시간순 계산한다.
    그래서 회원의 sport_rating = 마지막 이력, 반영 경기 수 = 이력 행 수이고, 팀 등급도 이 값에서 나온다.
  * 시연 동호회 2개(발표·리허설): 동호회장 A, 가장 먼저 가입한 동호회원 B, 11/29 매칭방에 팀으로 신청한 C(대표)~G.
    11/29 신청은 앱이 팀 신청 때 만드는 모양 그대로다(대표 행 group_application_id = 자기 번호, 팀원 행 = 대표 번호,
    참가자 행은 시설 담당자가 승인할 때 생기므로 넣지 않는다).
"""
import random
from datetime import date, datetime, time, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

SEED = 20369107  # 조건에 맞는 일정이 바로 나오는 시드(20261112부터 찾음). demo-match-data(20261123)와 달라야 접수 번호가 안 겹친다
OUT = Path(__file__).resolve().parent.parent / 'db' / 'migrations' / '20261112-club-demo-data.sql'

# 비밀번호 spoit1234의 BCrypt 값(spring-security-crypto 6.5.11 BCrypt.hashpw, strength 10)
PASSWORD_HASH = '$2a$10$pSiGR361K/rv0YEnNbH/keZbZxx7hwMkuC/6YDWu2TTBY3pzRPppi'
ROLE = 'MEMBER'

# RatingPolicy와 같은 값
PLACEMENT_MATCHES = 5
K_FACTOR, PLACEMENT_K_FACTOR = 32, 64
ELO_DIVISOR = 400
ALGORITHM_VERSION = 'ELO-K32-V2'
TIER_START_MMR, TIER_SIZE = 800, 200  # 단계 50 × 4단계
TIERS = ['브론즈', '실버', '골드', '플래티넘', '다이아']
START_MMR = Decimal('1000.00')

MATCH_FROM = date(2026, 8, 1)
MATCH_UNTIL = date(2026, 11, 8)    # 이 날까지 경기. 반영은 다음 날
JOINED_UNTIL = date(2026, 11, 5)
MIN_GAP_DAYS = 3                    # 한 동호회의 경기 사이 최소 간격(직전 반영 뒤에 다음 신청·확정)
DRAW_RATE = 0.1
ROOM_DAY = date(2026, 11, 29)       # 발표(11/23 오후) 뒤라 발표 때도 모집 중
APPLIED_AT = datetime(2026, 11, 11, 21, 0)
DEMO_FACILITY = ('송파구', '잠실종합운동장 제2풋살경기장', '잠실 제2풋살경기장')

# 동호회: 이름, 구, 활동 요일(1=월~7=일), 시작, 종료, 정원, 인원, 창단일, 홈 구장(구장 이름, 제목에 쓸 짧은 이름),
#         실력(승률 계산용), 경기 수 목표, 시연 구분(None/1=발표/2=리허설), 소개
CLUBS = [
    dict(name='잠실 드림풋살', district='송파구', days='3,6', start='19:00', end='21:00', max=12, count=7,
         created=date(2026, 7, 4), home=('잠실종합운동장 풋살경기장', '잠실 풋살경기장'), strength=0.95, games=22, demo=None,
         intro='잠실종합운동장 풋살경기장에서 수요일·토요일 저녁마다 모이는 팀입니다.\n\n'
               '5:5 위주로 쿼터마다 팀을 섞어서 고르게 뛰어요. 꾸준히 운동해 온 분들이 많아 경기 수준이 높은 편이고, '
               '다른 동호회와의 매치도 일주일에 한두 번씩 있습니다.\n\n'
               '· 매주 수·토 19:00~21:00 (둘 중 편한 날 참석)\n· 구장비는 참석 인원끼리 나눠 내요\n· 지금은 풋살 경력 2년 이상인 분 위주로 받고 있어요'),
    dict(name='반포 리버사이드 FC', district='서초구', days='6,7', start='07:00', end='09:00', max=7, count=7,
         created=date(2025, 6, 14), home=('반포한강공원 축구장', '반포한강공원 축구장'), strength=0.70, games=8, demo=None,
         intro='주말 이른 아침 반포한강공원 축구장에서 공을 찹니다.\n\n'
               '· 토·일 07:00~09:00 (둘 중 편한 날 참석)\n· 경기 전 15분 같이 몸 풀기\n· 포지션은 돌아가며 맡아요\n\n'
               '아침 공기 마시며 뛰고 나면 하루가 길어져요. 지금은 정원이 다 찼습니다. 빈자리가 생기면 모집을 다시 열게요.'),
    dict(name='이촌 한강 FS', district='용산구', days='5', start='20:00', end='22:00', max=15, count=7,
         created=date(2026, 4, 25), home=('이촌한강공원 풋살경기장(옥수역하부)', '이촌한강공원 풋살장'), strength=0.65, games=8, demo=None,
         intro='이촌한강공원 풋살경기장(옥수역 아래)에서 금요일 저녁에 뜁니다.\n\n'
               '· 매주 금요일 20:00~22:00\n· 경기 전에 실력이 비슷하게 팀을 나눠요\n· 용산·성동·강남 어디서든 오기 편해요\n\n'
               '한 주를 마무리하는 운동이라 분위기는 편한 편이에요. 한 달에 두 번 이상 나올 수 있는 분이면 좋겠습니다.'),
    dict(name='영등포 레인보우 FS', district='영등포구', days='4', start='20:00', end='22:00', max=15, count=7,
         created=date(2025, 11, 3), home=('영등포공원 풋살경기장', '영등포공원 풋살장'), strength=0.55, games=8, demo=None,
         intro='영등포공원 풋살경기장에서 목요일 저녁마다 뛰는 혼성 풋살팀입니다.\n\n'
               '남녀가 함께 뛰어서 패스 위주로 경기해요. 풋살을 2년 넘게 해 온 멤버가 많아 기본기가 탄탄한 편입니다. '
               '새로 오신 분은 처음 두세 번은 같은 팀에서 뛰며 적응을 돕습니다.\n\n'
               '· 매주 목요일 20:00~22:00\n· 분기마다 다른 동호회와 교류전'),
    dict(name='망원 선데이 FC', district='마포구', days='7', start='08:00', end='10:00', max=20, count=7,
         created=date(2025, 4, 12), home=('망원유수지 풋살장', '망원유수지 풋살장'), strength=0.50, games=8, demo=None,
         intro='일요일 아침 망원유수지 풋살장에서 공 차는 팀입니다.\n\n'
               '· 매주 일요일 08:00~10:00\n· 20대 후반~30대 직장인 위주, 중급 이상\n· 경기 끝나고 망원시장 아침 식사는 자유 참여\n\n'
               '요즘은 다른 동호회와 친선 매치도 자주 잡고 있어요. 지각·노쇼 없이 꾸준히 나오실 분이면 환영합니다.'),
    dict(name='BLUE WAVE FS', district='광진구', days='7', start='16:00', end='18:00', max=10, count=7,
         created=date(2026, 8, 23), home=('뚝섬한강공원 축구장 1', '뚝섬한강공원 축구장'), strength=0.45, games=8, demo=None,
         intro='뚝섬한강공원에서 일요일 오후마다 뛰는 BLUE WAVE입니다.\n\n'
               '빠른 패스와 압박을 좋아하는 팀이라 체력이 중요해요. 정기전 말고도 가끔 평일 번개가 있고, '
               '경기 뒤에는 한강에서 잠깐 쉬었다 갑니다.\n\n'
               '· 매주 일요일 16:00~18:00, 뚝섬한강공원 축구장 1\n· 유니폼은 가입 후 맞춰요(선택)'),
    dict(name='구일 FS', district='구로구', days='6', start='14:00', end='16:00', max=12, count=7,
         created=date(2026, 6, 20), home=('안양천 체육시설 풋살경기장(구일역 하부)', '안양천 풋살경기장'), strength=0.30, games=8, demo=None,
         intro='구일역 아래 안양천 풋살경기장에서 토요일 오후에 모입니다.\n\n'
               '· 매주 토요일 14:00~16:00\n· 구로·광명·영등포 주민 위주\n· 역 바로 아래라 찾아오기 쉬워요\n\n'
               '아직 손발을 맞춰 가는 팀이라 결과보다 즐겁게 뛰는 걸 더 중요하게 생각해요. '
               '처음 오시는 분은 한 번 같이 뛰어 보고 가입하셔도 됩니다.'),
    dict(name='OFFSIDE FC', district='동작구', days='2,5', start='20:00', end='22:00', max=7, count=7,
         created=date(2026, 7, 24), home=('노량진 축구장', '노량진 축구장'), strength=0.25, games=8, demo=None,
         intro='노량진 축구장에서 화·금 저녁에 뛰는 OFFSIDE FC입니다.\n\n'
               '동네 친구 몇 명이 시작해서 지금은 매주 두 번 모여요. 경기 영상을 찍어서 다 같이 돌려 보는 걸 좋아하고, '
               '지난 경기에서 아쉬웠던 점을 짧게 이야기하고 시작합니다.\n\n'
               '· 화·금 20:00~22:00\n· 정원이 차서 지금은 모집 마감입니다'),
    # 시연 동호회: 모두 송파구, 일요일, 정원 15, 10명. 11/29 매칭방은 동호회 활동 시간과 같은 시각에 연다.
    dict(name='가락 FS', district='송파구', days='7', start='10:00', end='12:00', max=15, count=10,
         created=date(2026, 3, 7), home=DEMO_FACILITY[1:], strength=0.60, games=11, demo=1,
         intro='가락동·문정동 이웃들이 일요일 아침마다 모여 공을 차는 풋살팀 가락 FS입니다.\n\n'
               '잠실종합운동장 제2풋살경기장에서 매주 5:5로 뛰어요. 시작 전 20분은 다 같이 몸을 풀고, '
               '쿼터마다 팀을 다시 나눠 한쪽으로 기울지 않게 맞춥니다. 끝나면 근처에서 가볍게 아침을 먹고 헤어져요.\n\n'
               '[이런 분을 찾아요]\n· 한 달에 두 번 이상 일요일 오전을 비울 수 있는 분\n· 풋살 경험 1년 안팎, 중급 정도 실력\n'
               '· 이기는 것보다 다치지 않는 걸 먼저 챙기는 분\n\n'
               '[활동 안내]\n· 정기 모임: 매주 일요일 10:00~12:00\n· 다른 동호회와 친선 매치: 한 달에 두세 번\n'
               '· 구장비는 그날 나온 사람끼리 나눠 냅니다\n\n'
               '가입 신청에 주 포지션과 풋살 경력을 적어 주세요. 동호회장이 확인하고 단체 채팅방 링크를 보내 드려요.'),
    dict(name='코사 FS', district='송파구', days='7', start='18:00', end='20:00', max=15, count=10,
         created=date(2026, 6, 13), home=DEMO_FACILITY[1:], strength=0.60, games=11, demo=2,
         intro='일요일 저녁, 한 주를 땀으로 마무리하고 싶은 사람들이 모인 풋살팀 코사 FS입니다.\n\n'
               '회사도 사는 곳도 제각각이지만 일요일 저녁만큼은 꼭 모여요. 처음 오신 분도 첫 쿼터부터 바로 뛰고, '
               '그날 온 사람 실력에 맞춰 팀을 나눕니다.\n\n'
               '[이런 분을 찾아요]\n· 일요일 저녁 시간을 꾸준히 낼 수 있는 분\n· 초급부터 중급까지, 즐겁게 오래 뛰고 싶은 분\n'
               '· 송파·강동 쪽에서 오가기 편한 분\n\n'
               '[활동 안내]\n· 정기 모임: 매주 일요일 18:00~20:00, 잠실종합운동장 제2풋살경기장\n· 한 달에 한두 번 다른 동호회와 매치\n'
               '· 경기 뒤 저녁 식사는 원하는 분만\n\n'
               '가입 신청에 간단한 자기소개를 남겨 주시면 동호회장이 확인하고 연락드릴게요.'),
]
DEMO_ACCOUNTS = {1: ['club-00', 'club-01', 'club-02'], 2: ['club-03', 'club-04', 'club-05']}  # A·B·C
ROOM_TITLE = {'10:00': '잠실 제2풋살경기장 일요일 오전 5:5', '18:00': '잠실 제2풋살경기장 일요일 저녁 5:5'}
WEEKDAY = '월화수목금토일'
SPORT_VAR = {'축구+풋살': '@cd_s'}

SURNAMES = ['김', '이', '박', '최', '정', '강', '조', '윤', '장', '임', '한', '오', '서', '신', '권', '황', '안', '송',
            '류', '홍', '전', '고', '문', '양', '손', '배', '백', '허', '유', '남']
GIVEN = ['민준', '서준', '도윤', '예준', '시우', '하준', '주원', '지호', '지후', '준서', '준우', '현우', '도현', '지훈',
         '건우', '우진', '선우', '서진', '민재', '현준', '연우', '유준', '정우', '승우', '승현', '시윤', '준혁', '은우',
         '지환', '승민', '지우', '서연', '서윤', '지민', '수아', '하은', '민서', '지유', '윤서', '채원', '다은', '예은',
         '수빈', '지아', '소율', '예린', '하린', '민지', '태윤', '재원', '성민', '동현', '상훈', '영호', '진수', '경민',
         '형준', '대현', '태현', '재민']


# ---------- 레이팅 계산 (RatingCalculator와 같은 반올림) ----------
def team_average(entries):
    return (sum(entries) / Decimal(len(entries))).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def expected_score(own, opponent):
    exponent = float(opponent - own) / ELO_DIVISOR
    value = 1 / (1 + 10 ** exponent)
    # BigDecimal.valueOf(double)은 Double.toString(가장 짧은 표기)을 쓴다. Python repr도 같다.
    return Decimal(repr(value)).quantize(Decimal('0.0001'), rounding=ROUND_HALF_UP)


def k_factor(rated_count):
    return PLACEMENT_K_FACTOR if rated_count < PLACEMENT_MATCHES else K_FACTOR


def elo_delta(k, actual, expected):
    return (Decimal(k) * (actual - expected)).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def tier(avg):
    if avg is None:
        return '배치 중'
    return TIERS[min(max(avg - TIER_START_MMR, 0) // TIER_SIZE, len(TIERS) - 1)]


def sql_avg(values):
    """MySQL ROUND(AVG(DECIMAL))와 같은 값(반올림)."""
    if not values:
        return None
    return int((sum(values) / Decimal(len(values))).quantize(Decimal('1'), rounding=ROUND_HALF_UP))


# ---------- SQL 도우미 ----------
def q(value):
    if value is None:
        return 'NULL'
    if isinstance(value, (int, Decimal)):
        return str(value)
    text = str(value).replace('\\', '\\\\').replace("'", "''").replace('\n', '\\n')
    return "'%s'" % text


def dt(value):
    return value.strftime('%Y-%m-%d %H:%M:%S')


def hm(text):
    return time(*map(int, text.split(':')))


def union(rows, columns, where='@cd_ready = 1'):
    lines = []
    for i, row in enumerate(rows):
        if i == 0:
            lines.append('                 SELECT %s' % ', '.join('%s AS %s' % (v, c) for v, c in zip(row, columns)))
        else:
            lines.append('       UNION ALL SELECT %s' % ', '.join(row))
    return 'SELECT * FROM (\n%s\n) v\n WHERE %s;\n' % ('\n'.join(lines), where)


# ---------- 데이터 만들기 ----------
def slot_label(start):
    hour = hm(start).hour
    return '오전' if hour < 12 else ('오후' if hour < 17 else '저녁')


def schedule(rng):
    """동호회끼리 경기 날짜를 정한다. 홈 동호회의 활동 요일·시간·구장에서 한다."""
    n = len(CLUBS)
    remaining = [c['games'] for c in CLUBS]
    last_day = [None] * n
    meetings = [[0] * n for _ in range(n)]
    active_from = [max(MATCH_FROM, c['created'] + timedelta(days=21)) for c in CLUBS]
    matches = []
    day = MATCH_FROM
    while day <= MATCH_UNTIL:
        days_left = (MATCH_UNTIL - day).days + 1

        def ahead(i):
            # 기간 전체에 고르게 퍼지도록, 진도보다 앞서면 쉰다(시연 동호회는 더 촘촘히)(그래야 마지막 경기가 11월 초에 온다)
            span = (MATCH_UNTIL - active_from[i]).days + 1
            played = CLUBS[i]['games'] - remaining[i]
            return played > CLUBS[i]['games'] * ((day - active_from[i]).days + 1) / span + (0 if CLUBS[i]['demo'] else 0.5)

        def free(i):
            return (remaining[i] > 0 and day >= active_from[i] and not ahead(i)
                    and (last_day[i] is None or (day - last_day[i]).days >= MIN_GAP_DAYS))

        def urgency(i):
            # 남은 경기 수 ÷ 남은 기간에 칠 수 있는 경기 수. 클수록 밀린 동호회
            return remaining[i] / max(1.0, (MATCH_UNTIL - max(day, active_from[i])).days / MIN_GAP_DAYS + 1)
        hosts = [i for i in range(n) if str(day.isoweekday()) in CLUBS[i]['days'].split(',') and free(i)]
        rng.shuffle(hosts)
        for h in hosts:
            if not free(h):
                continue
            others = [o for o in range(n) if o != h and free(o)]
            if not others:
                continue
            # 밀린 동호회가 있을수록 자주 잡는다
            if rng.random() > min(1.0, 1.5 * max(urgency(h), max(urgency(o) for o in others))):
                continue
            weights = [urgency(o) ** 3 / (1 + 2 * meetings[h][o]) for o in others]
            o = rng.choices(others, weights)[0]
            matches.append({'home': h, 'away': o, 'day': day})
            for i in (h, o):
                remaining[i] -= 1
                last_day[i] = day
            meetings[h][o] += 1
            meetings[o][h] += 1
        day += timedelta(days=1)
    return matches if not any(remaining) else None


def build(rng):
    """경기 일정 → 동호회원 → 경기마다 출전·결과·레이팅. 조건에 맞지 않으면 None."""
    matches = schedule(rng)
    if matches is None:
        return None
    used_names = set()

    def nickname():
        while True:
            name = rng.choice(SURNAMES) + rng.choice(GIVEN)
            if name not in used_names:
                used_names.add(name)
                return name

    users, members_of = [], []
    first_day = {}
    for m in matches:
        for i in (m['home'], m['away']):
            first_day.setdefault(i, m['day'])
    member_no = 0
    for ci, c in enumerate(CLUBS):
        # 동호회장은 창단일, 나머지는 첫 경기 전에 가입. 한 명은 늦게 들어와 배치 중으로 남을 수 있다.
        early_until = min(first_day[ci] - timedelta(days=4), JOINED_UNTIL)
        start = c['created'] + timedelta(days=1)
        joined = [datetime.combine(c['created'], time(10, 0))]
        late = c['count'] - 1 if ci % 2 == 0 or c['demo'] else None
        for k in range(1, c['count']):
            if k == late:
                lo, hi = first_day[ci] + timedelta(days=30), JOINED_UNTIL
            else:
                lo, hi = start, early_until
            d = lo + timedelta(days=rng.randint(0, max((hi - lo).days, 0)))
            joined.append(datetime.combine(d, time(rng.randint(9, 22), rng.choice([0, 30]))))
        joined = [joined[0]] + sorted(joined[1:])
        ids = []
        for k in range(c['count']):
            if c['demo'] and k < 3:
                email = '%s@spoit.test' % DEMO_ACCOUNTS[c['demo']][k]
            else:
                member_no += 1
                email = 'club-member-%03d@spoit.test' % member_no
            no = len(users) + 1
            users.append({'no': no, 'email': email, 'nickname': nickname(), 'district': c['district'], 'club': ci,
                          'phone': '010-88%02d-%04d' % (no % 100, rng.randint(1000, 9999)),
                          'created_at': joined[k] - timedelta(days=rng.randint(1, 30), hours=rng.randint(0, 8)),
                          'joined_at': joined[k], 'role': '동호회장' if k == 0 else '동호회원',
                          'mmr': START_MMR, 'count': 0, 'available': joined[k], 'rated_at': None})
            ids.append(no)
        members_of.append(ids)

    for m in matches:
        h, a = m['home'], m['away']
        hc = CLUBS[h]
        start = datetime.combine(m['day'], hm(hc['start']))
        end = datetime.combine(m['day'], hm(hc['end']))
        m.update(start=start, end=end, facility=hc['home'], title='%s %s요일 %s 5:5' % (
            hc['home'][1], WEEKDAY[m['day'].isoweekday() - 1], slot_label(hc['start'])))
        sides = []
        for side, ci in (('A', h), ('B', a)):
            lead = start - timedelta(days=rng.randint(2, 4), hours=rng.randint(0, 10))
            eligible = [u for u in members_of[ci] if users[u - 1]['joined_at'] < lead - timedelta(hours=1)]
            if len(eligible) < 5:
                return None
            rng.shuffle(eligible)
            # 시연 동호회의 A(동호회장)는 매번 뛰어 배치를 마치게 한다(발표 때 마이페이지가 "배치 중"이 아니게)
            core = [members_of[ci][0]] if CLUBS[ci]['demo'] and members_of[ci][0] in eligible else []
            chosen = core + sorted((u for u in eligible if u not in core), key=lambda u: users[u - 1]['count'])[:5 - len(core)]
            created = max([lead] + [users[u - 1]['available'] for u in chosen])
            if created > start - timedelta(hours=6):
                return None
            leader = members_of[ci][0]
            rep = leader if leader in chosen else rng.choice(chosen)
            team = [rep] + [u for u in chosen if u != rep]
            rows = []
            for u in team:
                confirmed = created + timedelta(minutes=rng.randint(60, 180))
                rows.append({'user': u, 'club': ci, 'side': side, 'created': created, 'confirmed': confirmed,
                             'entry': users[u - 1]['mmr']})
            sides.append(rows)
        m['apps'] = sides[0] + sides[1]  # 동호회마다 대표가 먼저
        parts = sorted(m['apps'], key=lambda r: (r['confirmed'], r['user']))
        for pid, r in enumerate(parts, 1):
            r['pid'] = pid
        for side in 'AB':
            team = [r for r in m['apps'] if r['side'] == side]
            m['captain_' + side] = min(team, key=lambda r: (-r['entry'], r['pid']))['pid']
        sa = CLUBS[h]['strength'] - CLUBS[a]['strength']
        r = rng.random()
        if r < DRAW_RATE:
            m['result'] = 'DRAW'
        else:
            p_home = 1 / (1 + 10 ** (-sa / 0.35))
            m['result'] = 'A_WIN' if (r - DRAW_RATE) / (1 - DRAW_RATE) < p_home else 'B_WIN'
        m['sub_A'] = end + timedelta(minutes=rng.randint(20, 90))
        m['sub_B'] = end + timedelta(minutes=rng.randint(30, 180))
        m['result_at'] = max(m['sub_A'], m['sub_B'])
        m['rated_at'] = end + timedelta(hours=24)  # 평가 없이 결과 마감에 반영(closeResult)
        avg = {s: team_average([r['entry'] for r in m['apps'] if r['side'] == s]) for s in 'AB'}
        exp = {'A': expected_score(avg['A'], avg['B']), 'B': expected_score(avg['B'], avg['A'])}
        m['avg'] = avg
        m['history'] = []
        for r in sorted(m['apps'], key=lambda r: r['user']):  # RatingApplyService는 user_id 오름차순
            u = users[r['user'] - 1]
            actual = Decimal('0.5') if m['result'] == 'DRAW' else (
                Decimal('1') if m['result'] == r['side'] + '_WIN' else Decimal('0'))
            delta = elo_delta(k_factor(u['count']), actual, exp[r['side']])
            before = u['mmr']
            u['mmr'] = before + delta
            u['count'] += 1
            u['rated_at'] = m['rated_at']
            u['available'] = m['rated_at'] + timedelta(hours=rng.randint(1, 6))
            m['history'].append({'pid': r['pid'], 'user': r['user'], 'before': before, 'after': u['mmr'],
                                 'delta': delta, 'actual': actual, 'expected': exp[r['side']]})
    return matches, users, members_of


def club_average(users, ids):
    return sql_avg([users[u - 1]['mmr'] for u in ids if users[u - 1]['count'] >= PLACEMENT_MATCHES])


def acceptable(result):
    """골드 1곳(잠실), 브론즈 2~5곳, 나머지 실버. 시연 동호회는 실버이고 A가 빠져도 실버.
    전승·전패 동호회는 실제처럼 보이지 않아 뺀다."""
    if result is None:
        return False
    matches, users, members_of = result
    tiers = [tier(club_average(users, ids)) for ids in members_of]
    if '배치 중' in tiers or tiers.count('골드') != 1 or tiers[0] != '골드' or not 2 <= tiers.count('브론즈') <= 5:
        return False
    if any(t in ('플래티넘', '다이아') for t in tiers):
        return False
    for ci in range(len(CLUBS)):
        played = [m for m in matches if ci in (m['home'], m['away'])]
        wins = sum(1 for m in played if m['result'] == ('A_WIN' if m['home'] == ci else 'B_WIN'))
        losses = sum(1 for m in played if m['result'] == ('B_WIN' if m['home'] == ci else 'A_WIN'))
        if wins == 0 or losses == 0:
            return False
    for ci, c in enumerate(CLUBS):
        if c['demo']:
            ids = members_of[ci]
            # 실버 경계(1000) 바로 위면 시연 중에 브론즈처럼 보일 수 있어 여유를 둔다
            if not (1020 <= club_average(users, ids) < 1170 and 1020 <= club_average(users, ids[1:]) < 1170):
                return False
            # 시연에 로그인하는 A·B는 배치를 마쳐야 한다
            if any(users[u - 1]['count'] < PLACEMENT_MATCHES for u in ids[:2]):
                return False
            # 11/29 팀 신청 C~G 중 배치를 마친 사람이 있어야 화면이 자연스럽다
            if sum(users[u - 1]['count'] >= PLACEMENT_MATCHES for u in ids[2:7]) < 3:
                return False
            # 지난 일정 맨 위가 최근 경기여야 "지금 활동 중인 동호회"로 보인다
            if max(m['day'] for m in matches if ci in (m['home'], m['away'])) < date(2026, 11, 1):
                return False
    return True


def find():
    for attempt in range(200000):
        rng = random.Random(SEED + attempt)
        result = build(rng)
        if acceptable(result):
            return rng, result, attempt
    raise SystemExit('조건에 맞는 일정을 찾지 못했습니다.')


def receipt_maker(rng):
    alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    used = set()

    def receipt(at):
        while True:
            value = 'RCPT-%s-%s' % (at.strftime('%Y%m%d'), ''.join(rng.choice(alphabet) for _ in range(8)))
            if value not in used:
                used.add(value)
                return value
    return receipt


def main():
    rng, (matches, users, members_of), attempt = find()
    receipt = receipt_maker(rng)
    for c in CLUBS:
        c['sport_var'] = SPORT_VAR['축구+풋살']
    for u in users:
        u['sport_var'] = SPORT_VAR['축구+풋살']
    for c in CLUBS:
        assert len(c['intro']) <= 500, c['name']
        assert c['count'] <= c['max'] and 2 <= c['max'] <= 30, c['name']
        c['status'] = '마감' if c['count'] >= c['max'] else '모집중'
    districts = sorted({c['district'] for c in CLUBS})
    facilities = []
    for c in CLUBS:
        key = (c['district'], c['home'][0])
        if key not in facilities:
            facilities.append(key)
    fac_var = {f[1]: '@cd_f%d' % (i + 1) for i, f in enumerate(facilities)}
    club_names = [c['name'] for c in CLUBS]
    marker = '%s@spoit.test' % DEMO_ACCOUNTS[1][0]

    # 지난 경기 행 번호
    app_no = part_no = 0
    groups = []
    for mi, m in enumerate(matches, 1):
        m['no'] = mi
        base = part_no
        for r in m['apps']:
            app_no += 1
            r['app'] = app_no
            r['receipt'] = receipt(r['created'])
        for side in 'AB':
            team = [r['app'] for r in m['apps'] if r['side'] == side]
            groups.append((team[0], team[-1]))
        for r in m['apps']:
            r['participant'] = base + r['pid']
        part_no += len(m['apps'])
    # 11/29 매칭방과 C~G 팀 신청(대기)
    rooms, waiting = [], []
    for ci, c in enumerate(CLUBS):
        if not c['demo']:
            continue
        rooms.append({'club': ci, 'start': c['start'], 'end': c['end'], 'title': ROOM_TITLE[c['start']],
                      'no': len(matches) + len(rooms) + 1})
        applied = APPLIED_AT + timedelta(minutes=17 * c['demo'])
        team = members_of[ci][2:7]
        first = app_no + 1
        for u in team:
            app_no += 1
            waiting.append({'app': app_no, 'room': rooms[-1]['no'], 'user': u, 'club': ci,
                            'receipt': receipt(applied), 'created': applied})
        groups.append((first, app_no))
    receipts = [r['receipt'] for m in matches for r in m['apps']] + [w['receipt'] for w in waiting]
    assert len(set(receipts)) == len(receipts)

    out = []
    w = out.append
    history_rows = sum(len(m['history']) for m in matches)
    w('-- =====================================================================\n')
    w('-- 동호회 시연 데이터: 축구+풋살 동호회 %d개(시연 2개 포함), 회원 %d명,\n'
      '--                   지난 동호회 경기 %d개(레이팅 이력 %d행), 11/29 매칭방 %d개와 팀 신청 %d행\n'
      % (len(CLUBS), len(users), len(matches), history_rows, len(rooms), len(waiting)))
    w('-- 이 파일은 scripts/club-demo-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.\n')
    w('-- 대상: 20261110-seoul-public-facilities.sql과 20261112-club-single-region.sql을 적용한 DB (로컬·운영)\n')
    w('-- 내용\n')
    w('--   * 축구+풋살 동호회 10개: 이름·소개·활동 시간·인원이 실제 동호회처럼 다르다. 비밀번호는 모두 spoit1234.\n')
    w('--   * 시연 동호회 가락 FS(발표)와 코사 FS(리허설): club-00·03 = 동호회장 A, club-01·04 = B(가장 먼저 가입),\n')
    w('--     club-02·05 = C(11/29 팀 신청 대표). 나머지 회원은 club-member-NNN@spoit.test.\n')
    w('--   * 지난 경기는 2026-08~11월 초 동호회끼리 치른 5:5(홈 동호회의 구장·활동 시간). 팀 신청 확정 → 참가자 → 두 팀장 결과 제출 →\n')
    w('--     결과 마감(종료 24시간 뒤)에 평가 없이 레이팅 반영까지 끝난 종료 상태다. 이력은 RatingCalculator와 같은 식으로\n')
    w('--     1000부터 시간순 계산했고, sport_rating은 마지막 이력 값·이력 행 수다. 팀 등급은 이 레이팅에서 나온다.\n')
    w('--   * 시연 동호회마다 11/29(일) 잠실종합운동장 제2풋살경기장 매칭방 1개와 C~G 팀 신청(대기).\n')
    w('--     → 발표 때 "모집 중인 팀 매치에 신청된 동호회원은 추방할 수 없습니다"가 나온다.\n')
    w('-- 원칙\n')
    w('--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.\n')
    w('--   * %s가 이미 있으면 적용된 것으로 보고 아무것도 넣지 않는다(오류 없음).\n' % marker)
    w('--   * 종목·지역·시설·담당자는 이름으로 찾는다. 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다. 기존 행은 바꾸지 않는다.\n')
    w('-- =====================================================================\n\n')
    w('SET NAMES utf8mb4;\nSTART TRANSACTION;\n\n')

    w('-- 0. 이름으로 번호 찾기\n')
    for name, var in SPORT_VAR.items():
        w("SET %s = (SELECT MAX(sport_id) FROM sport WHERE name = %s);\n" % (var, q(name)))
    for d in districts:
        w("SET @cd_r_%s = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = %s);\n" % (d, q(d)))
    for gu, name in facilities:
        v = fac_var[name]
        w("SET %s = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 "
          "WHERE r.name = %s AND f.name = %s AND f.deleted_at IS NULL);\n" % (v, q(gu), q(name)))
        w("SET %s_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id "
          "WHERE fm.facility_id = %s AND u.email LIKE 'facility-__@spoit.test');\n" % (v, v))
    w('\n')

    slots = [(fac_var[m['facility'][0]], m['start'], m['end']) for m in matches]
    demo_fac = fac_var[DEMO_FACILITY[1]]
    slots += [(demo_fac, datetime.combine(ROOM_DAY, hm(r['start'])), datetime.combine(ROOM_DAY, hm(r['end']))) for r in rooms]
    emails = [u['email'] for u in users if u['email'] != marker]
    w('-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.\n')
    w('SELECT (%s),\n' % ' AND '.join('%s IS NOT NULL' % v for v in SPORT_VAR.values()))
    w('       (%s) = %d,\n' % (' + '.join('(@cd_r_%s IS NOT NULL)' % d for d in districts), len(districts)))
    w('       (%s) = %d,\n' % (' + '.join(
        '(%s IS NOT NULL AND %s_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = %s AND sport_id = @cd_s))'
        % (v, v, v) for v in fac_var.values()), len(fac_var)))
    w('       NOT EXISTS (SELECT 1 FROM club WHERE active_club_name IN (%s)),\n' % ', '.join(q(n) for n in club_names))
    w("       NOT EXISTS (SELECT 1 FROM social_match WHERE status <> '취소' AND (\n%s)),\n" % ' OR\n'.join(
        '         (facility_id = %s AND start_at < %s AND end_at > %s)' % (v, q(dt(e)), q(dt(s))) for v, s, e in slots))
    w('       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN (%s)),\n' % ', '.join(q(r) for r in receipts))
    w('       NOT EXISTS (SELECT 1 FROM app_user WHERE email IN (%s)),\n' % ', '.join(q(e) for e in emails))
    w('       EXISTS (SELECT 1 FROM app_user WHERE email = %s)\n' % q(marker))
    w('  INTO @cd_sport, @cd_districts, @cd_facilities, @cd_club_names_free, @cd_slots_free, @cd_receipts_free, '
      '@cd_emails_free, @cd_already_applied;\n')
    w('SET @cd_checks = @cd_sport AND @cd_districts AND @cd_facilities AND @cd_club_names_free AND @cd_slots_free '
      'AND @cd_receipts_free AND @cd_emails_free;\n')
    w('SET @cd_ready = @cd_checks AND NOT @cd_already_applied;\n')
    w('SELECT @cd_sport AS sport, @cd_districts AS districts, @cd_facilities AS facilities, @cd_club_names_free AS club_names_free,\n'
      '       @cd_slots_free AS slots_free, @cd_receipts_free AS receipts_free, @cd_emails_free AS emails_free,\n'
      '       @cd_already_applied AS already_applied;\n')
    w('-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.\n')
    w('DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @cd_checks = 0 AND @cd_already_applied = 0);\n\n')

    w('SELECT MAX(user_id), (SELECT COALESCE(MAX(club_id), 0) FROM club), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),\n'
      '       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),\n'
      '       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant)\n'
      '  INTO @cd_u, @cd_c, @cd_m, @cd_sm, @cd_a, @cd_p\n  FROM app_user;\n\n')

    w('-- 1. 회원 (비밀번호 spoit1234)\n')
    w('INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)\n')
    w(union([['(@cd_u + %d)' % u['no'], q(u['email']), q(PASSWORD_HASH), q(u['nickname']), q(u['phone']), q(ROLE),
              q(dt(u['created_at']))] for u in users],
            ['user_id', 'email', 'password_hash', 'nickname', 'phone', 'role', 'created_at']))
    w('\nINSERT INTO user_region (user_id, region_id)\n')
    w(union([['(@cd_u + %d)' % u['no'], '@cd_r_%s' % u['district']] for u in users], ['user_id', 'region_id']))
    w('\nINSERT INTO user_preferred_sport (user_id, sport_id)\n')
    w(union([['(@cd_u + %d)' % u['no'], u['sport_var']] for u in users], ['user_id', 'sport_id']))
    w('\n-- 종목별 레이팅 = 마지막 레이팅 이력. rated_match_count가 5 이상이면 배치 완료로 팀 등급 평균에 들어간다.\n')
    w('INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)\n')
    w(union([['(@cd_u + %d)' % u['no'], u['sport_var'], str(u['mmr']), str(u['count']), q(dt(u['rated_at'] or u['joined_at']))]
             for u in users], ['user_id', 'sport_id', 'current_mmr', 'rated_match_count', 'updated_at']))

    w('\n-- 2. 동호회 (정원이 찬 동호회는 마감)\n')
    w('INSERT INTO club (club_id, sport_id, club_name, intro, max_members, recruit_status, activity_days, activity_start_time, activity_end_time, created_at)\n')
    w(union([['(@cd_c + %d)' % (i + 1), c['sport_var'], q(c['name']), q(c['intro']), str(c['max']), q(c['status']), q(c['days']),
              q(c['start'] + ':00'), q(c['end'] + ':00'), q(dt(datetime.combine(c['created'], time(10, 0))))]
             for i, c in enumerate(CLUBS)],
            ['club_id', 'sport_id', 'club_name', 'intro', 'max_members', 'recruit_status', 'activity_days',
             'activity_start_time', 'activity_end_time', 'created_at']))
    w('\nINSERT INTO club_region (club_id, region_id)\n')
    w(union([['(@cd_c + %d)' % (i + 1), '@cd_r_%s' % c['district']] for i, c in enumerate(CLUBS)], ['club_id', 'region_id']))
    w('\n-- 동호회장은 창단일에 가입, 동호회원 목록은 동호회장 → 가입 순서라 시연 동호회의 B가 동호회장 바로 아래에 보인다.\n')
    w('INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)\n')
    w(union([['(@cd_m + %d)' % u['no'], '(@cd_c + %d)' % (u['club'] + 1), '(@cd_u + %d)' % u['no'], q(u['role']), q(dt(u['joined_at']))]
             for u in users], ['club_member_id', 'club_id', 'user_id', 'role', 'joined_at']))

    w('\n-- 3. 경기: 지난 동호회 경기(종료, 결과 확정, 레이팅 반영) + 11/29 매칭방(모집 중). 팀장은 참가자를 넣은 뒤 지정한다.\n')
    w('INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, '
      'team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, photo_url, assignment_method, assigned_by, '
      'assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, rating_processed_at)\n')
    rows = []
    for m in matches:
        v = fac_var[m['facility'][0]]
        rows.append(['(@cd_sm + %d)' % m['no'], v, '@cd_s', v + '_mgr', q(m['title']), q(dt(m['start'])), q(dt(m['end'])),
                     '10', '10', '5', '5', '0', q('중급'), q('종료'), q('/images/matches/soccer.jpg'), q('MANUAL'), v + '_mgr',
                     q(dt(m['start'] - timedelta(hours=1))), q('CONFIRMED'), q(m['result']), q(dt(m['result_at'])),
                     q('APPLIED'), q(dt(m['rated_at']))])
    for r in rooms:
        rows.append(['(@cd_sm + %d)' % r['no'], demo_fac, '@cd_s', demo_fac + '_mgr', q(r['title']),
                     q('%s %s:00' % (ROOM_DAY, r['start'])), q('%s %s:00' % (ROOM_DAY, r['end'])), '10', '10', '5', '5', '0',
                     q('중급'), q('모집 중'), q('/images/matches/soccer.jpg'), q('MANUAL'), 'NULL', 'NULL', 'NULL', 'NULL', 'NULL',
                     q('WAITING'), 'NULL'])
    w(union(rows, ['match_id', 'facility_id', 'sport_id', 'created_by', 'title', 'start_at', 'end_at', 'min_participants',
                   'max_participants', 'team_a_capacity', 'team_b_capacity', 'fee_per_person', 'required_skill', 'status',
                   'photo_url', 'assignment_method', 'assigned_by', 'assignment_confirmed_at', 'result_status', 'final_result',
                   'result_confirmed_at', 'rating_status', 'rating_processed_at']))

    w('\n-- 4. 팀 신청: 동호회마다 대표 1명 + 팀원 4명. 지난 경기는 시설 담당자가 확정, 11/29는 대기. 행을 넣은 뒤 묶음 번호를 붙인다.\n')
    w('INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)\n')
    rows = [['(@cd_a + %d)' % r['app'], '(@cd_sm + %d)' % m['no'], '(@cd_u + %d)' % r['user'], '(@cd_c + %d)' % (r['club'] + 1),
             q('동호회'), q('확정'), q(r['receipt']), q(dt(r['created'])), q(dt(r['confirmed']))] for m in matches for r in m['apps']]
    rows += [['(@cd_a + %d)' % a['app'], '(@cd_sm + %d)' % a['room'], '(@cd_u + %d)' % a['user'], '(@cd_c + %d)' % (a['club'] + 1),
              q('동호회'), q('대기'), q(a['receipt']), q(dt(a['created'])), 'NULL'] for a in waiting]
    w(union(rows, ['application_id', 'match_id', 'applicant_user_id', 'club_id', 'application_type', 'status', 'receipt_no',
                   'created_at', 'confirmed_at']))
    cases = '\n'.join('         WHEN application_id BETWEEN (@cd_a + %d) AND (@cd_a + %d) THEN (@cd_a + %d)' % (f, l, f) for f, l in groups)
    w('UPDATE match_application\n   SET group_application_id = CASE\n%s\n       END\n' % cases)
    w(' WHERE @cd_ready = 1 AND application_id BETWEEN (@cd_a + 1) AND (@cd_a + %d);\n\n' % app_no)

    w('-- 5. 참가자 (확정 순서대로 번호, entry_mmr은 확정 시점의 레이팅 = 직전 경기 반영 뒤 값)\n')
    w('INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)\n')
    rows = []
    for m in matches:
        for r in sorted(m['apps'], key=lambda r: r['pid']):
            rows.append(['(@cd_p + %d)' % r['participant'], '(@cd_a + %d)' % r['app'], '(@cd_sm + %d)' % m['no'],
                         '(@cd_u + %d)' % r['user'], q('확정'), q(r['side']), str(r['entry']), q(dt(r['confirmed']))])
    w(union(rows, ['participant_id', 'application_id', 'match_id', 'user_id', 'participation_status', 'match_side',
                   'entry_mmr', 'joined_at']))
    w('-- 팀장: 팀에서 entry_mmr이 가장 높은 참가자(같으면 번호가 작은 쪽, TeamCaptainSelector)\n')
    base = {m['no']: m['apps'][0]['participant'] - m['apps'][0]['pid'] for m in matches}
    w('UPDATE social_match\n   SET captain_a_participant_id = CASE match_id\n%s\n       END,\n'
      % '\n'.join('         WHEN (@cd_sm + %d) THEN (@cd_p + %d)' % (m['no'], base[m['no']] + m['captain_A']) for m in matches))
    w('       captain_b_participant_id = CASE match_id\n%s\n       END\n'
      % '\n'.join('         WHEN (@cd_sm + %d) THEN (@cd_p + %d)' % (m['no'], base[m['no']] + m['captain_B']) for m in matches))
    w(' WHERE @cd_ready = 1 AND match_id BETWEEN (@cd_sm + 1) AND (@cd_sm + %d);\n\n' % len(matches))

    w('-- 6. 두 팀장의 결과 제출 (같은 결과라 확정)\n')
    w('INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)\n')
    rows = []
    for m in matches:
        for side in 'AB':
            rows.append(['(@cd_sm + %d)' % m['no'], '(@cd_p + %d)' % (base[m['no']] + m['captain_' + side]), q(m['result']),
                         q('CONFIRMED'), q(dt(m['sub_' + side]))])
    w(union(rows, ['match_id', 'captain_participant_id', 'reported_result', 'submission_status', 'submitted_at']))

    w('\n-- 7. 레이팅 이력 (평가 없이 반영: evaluation_delta 0, evaluation_score NULL)\n')
    w('INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id, mmr_before, mmr_after, elo_delta, evaluation_delta, '
      'final_delta, evaluation_score, actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version, calculated_at)\n')
    rows = []
    for m in matches:
        for h in m['history']:
            rows.append(['(@cd_sm + %d)' % m['no'], '(@cd_p + %d)' % (base[m['no']] + h['pid']), '(@cd_u + %d)' % h['user'], '@cd_s',
                         str(h['before']), str(h['after']), str(h['delta']), '0.00', str(h['delta']), 'NULL',
                         str(h['actual'].quantize(Decimal('0.1'))), str(h['expected']), str(m['avg']['A']), str(m['avg']['B']),
                         q(ALGORITHM_VERSION), q(dt(m['rated_at']))])
    w(union(rows, ['match_id', 'participant_id', 'user_id', 'sport_id', 'mmr_before', 'mmr_after', 'elo_delta', 'evaluation_delta',
                   'final_delta', 'evaluation_score', 'actual_score', 'expected_score', 'team_avg_mmr_a', 'team_avg_mmr_b',
                   'algorithm_version', 'calculated_at']))
    w('\nCOMMIT;\n\n')

    # 확인 질의와 기대값
    averages = [club_average(users, ids) for ids in members_of]
    tiers = {}
    for a in averages:
        tiers[tier(a)] = tiers.get(tier(a), 0) + 1
    names_sql = ', '.join(q(n) for n in club_names)
    user_filter = "u.email LIKE 'club-member-___@spoit.test' OR u.email IN (%s)" % ', '.join(
        q('%s@spoit.test' % e) for s in sorted(DEMO_ACCOUNTS) for e in DEMO_ACCOUNTS[s])
    w('-- 확인 1: 넣은 행 수 (이미 적용된 DB에서도 위임·탈퇴 전이면 같은 값)\n')
    w('SELECT (SELECT COUNT(*) FROM app_user u WHERE %s) AS club_demo_users,\n' % user_filter)
    w('       (SELECT COUNT(*) FROM club WHERE club_name IN (%s) AND deleted_at IS NULL) AS club_demo_clubs,\n' % names_sql)
    w('       (SELECT COUNT(*) FROM club_member m JOIN club c ON c.club_id = m.club_id WHERE c.club_name IN (%s) AND c.deleted_at IS NULL AND m.left_at IS NULL) AS active_members,\n' % names_sql)
    w("       (SELECT COUNT(DISTINCT ma.match_id) FROM match_application ma JOIN club c ON c.club_id = ma.club_id JOIN social_match sm ON sm.match_id = ma.match_id\n"
      "         WHERE c.club_name IN (%s) AND ma.status = '확정' AND sm.status = '종료') AS past_matches,\n" % names_sql)
    w('       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE %s) AS history_rows,\n' % user_filter)
    w("       (SELECT COUNT(*) FROM match_application WHERE receipt_no IN (%s) AND status = '대기' AND group_application_id IS NOT NULL) AS team_applications;\n"
      % ', '.join(q(a['receipt']) for a in waiting))
    w('-- 기대값: club_demo_users %d, club_demo_clubs %d, active_members %d, past_matches %d, history_rows %d, team_applications %d\n\n'
      % (len(users), len(CLUBS), len(users), len(matches), history_rows, len(waiting)))
    w('-- 확인 2: 레이팅이 이력과 맞는지 (sport_rating = 마지막 이력, 경기 수 = 이력 행 수). 0이면 맞다.\n')
    w('SELECT COUNT(*) AS rating_mismatches\n  FROM app_user u JOIN sport_rating sr ON sr.user_id = u.user_id\n'
      '  JOIN sport s ON s.sport_id = sr.sport_id AND s.name = \'축구+풋살\'\n'
      ' WHERE (%s)\n'
      '   AND (sr.rated_match_count <> (SELECT COUNT(*) FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id)\n'
      '        OR sr.current_mmr <> COALESCE((SELECT h.mmr_after FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id\n'
      '                                        ORDER BY h.calculated_at DESC, h.history_id DESC LIMIT 1), 1000));\n\n' % user_filter)
    w('-- 확인 3: 동호회별 인원과 팀 등급(배치를 마친 동호회원 평균, ClubMapper.xml averageMmr과 같은 식)\n')
    w('SELECT c.club_name, c.recruit_status, CONCAT(COUNT(m.club_member_id), \'/\', c.max_members) AS members,\n')
    w('       (SELECT ROUND(AVG(sr.current_mmr)) FROM club_member am JOIN sport_rating sr ON sr.user_id = am.user_id AND sr.sport_id = c.sport_id\n')
    w('         WHERE am.club_id = c.club_id AND am.left_at IS NULL AND sr.rated_match_count >= %d) AS average_mmr\n' % PLACEMENT_MATCHES)
    w('  FROM club c JOIN club_member m ON m.club_id = c.club_id AND m.left_at IS NULL\n')
    w(' WHERE c.club_name IN (%s) AND c.deleted_at IS NULL\n' % names_sql)
    w(' GROUP BY c.club_id ORDER BY average_mmr DESC;\n')
    w('-- 기대 등급(위임·탈퇴 전): %s\n' % ', '.join('%s %s(%s)' % (c['name'], tier(a), a) for c, a in zip(CLUBS, averages)))
    w('-- 등급별 동호회 수: %s\n' % ', '.join('%s %d' % (t, tiers.get(t, 0)) for t in TIERS))

    OUT.write_text(''.join(out), encoding='utf-8', newline='\n')
    counts = {
        'app_user': len(users), 'user_region': len(users), 'user_preferred_sport': len(users),
        'sport_rating': len(users), 'club': len(CLUBS), 'club_region': len(CLUBS), 'club_member': len(users),
        'social_match': len(matches) + len(rooms), 'match_application': app_no,
        'match_participant': part_no, 'match_result_submission': 2 * len(matches), 'sport_rating_history': history_rows,
    }
    print('wrote %s (attempt %d)' % (OUT, attempt))
    print('rows', sum(counts.values()), counts)
    print('tiers', tiers)
    for ci, c in enumerate(CLUBS):
        ids = members_of[ci]
        placed = sum(users[u - 1]['count'] >= PLACEMENT_MATCHES for u in ids)
        games = sum(1 for m in matches if ci in (m['home'], m['away']))
        wins = sum(1 for m in matches if (m['home'] == ci and m['result'] == 'A_WIN') or (m['away'] == ci and m['result'] == 'B_WIN'))
        print('  %-12s %s %d/%d placed %d games %d wins %d avg %s %s intro %d' % (
            c['name'], c['status'], c['count'], c['max'], placed, games, wins, averages[ci], tier(averages[ci]), len(c['intro'])))
        if c['demo']:
            print('     without A:', club_average(users, ids[1:]),
                  [(users[u - 1]['email'], users[u - 1]['nickname'], str(users[u - 1]['mmr']), users[u - 1]['count']) for u in ids])
    main_existing({u['nickname'] for u in users})


# =====================================================================
# 기존 동호회 데이터: seed 동호회에 새 회원·지난 경기·레이팅 이력·예정 일정을, seed·HY 님 데이터 동호회에 소개 글을 채운다.
# 출력은 따로(20261113-club-existing-data.sql). 시연 데이터와 다른 난수를 써서 시연 데이터 파일은 바뀌지 않는다.
# 기존 회원(seed 팀원 계정, demo-NNN)은 건드리지 않는다. 경기는 새 회원끼리만 뛴다.
# =====================================================================
EX_SEED = 20288860  # 조건에 맞는 일정이 바로 나오는 시드(20261113부터 찾음). 시연 데이터 시드와 달라야 접수 번호가 안 겹친다
EX_OUT = OUT.parent / '20261113-club-existing-data.sql'
EX_MARKER = 'club-user-001@spoit.test'
EX_MATCH_FROM = date(2026, 8, 1)
EX_MATCH_UNTIL = date(2026, 11, 8)
EX_ROOM_FROM = date(2026, 11, 24)   # 발표(11/23) 뒤라 발표 때도 모집 중
EX_ROOM_UNTIL = date(2026, 12, 6)
EX_APPLIED_AT = datetime(2026, 11, 12, 21, 30)
EX_GAMES = 6
# 다른 데이터(HY 님 매칭방 등)가 이미 쓰는 구장 시간. 사전 확인 slots_free에 걸린 것을 적어 두고 피한다.
EX_BUSY = {'중랑물재생센터 배드민턴장': [(datetime(2026, 11, 25, 18, 0), datetime(2026, 11, 25, 20, 0))]}
SPORT_PHOTO = {'축구+풋살': 'soccer', '농구': 'basketball', '배드민턴': 'badminton', '테니스': 'tennis', '족구': 'jokgu'}
TEAM_SIZE = {'축구+풋살': 5, '농구': 4, '배드민턴': 2, '테니스': 2, '족구': 3}
NEW_MEMBERS = {'축구+풋살': 7, '농구': 6, '배드민턴': 4, '테니스': 4, '족구': 4}
REGULARS = {'축구+풋살': 3, '농구': 2, '배드민턴': 1, '테니스': 1, '족구': 2}  # 매번 나오는 고정 멤버(배치를 마친다)
EX_SPORT_VAR = {'축구+풋살': '@cx_s_축구', '농구': '@cx_s_농구', '배드민턴': '@cx_s_배드민턴', '테니스': '@cx_s_테니스',
                '족구': '@cx_s_족구'}


def ex_intro(line, mood, want, days, start, end, place, extra, join):
    """소개 글 형식: 한 줄 → 분위기 → [이런 분을 찾아요] → [활동 안내] → 가입 안내."""
    text = '%s\n\n%s\n\n[이런 분을 찾아요]\n%s\n\n[활동 안내]\n· 정기 모임: 매주 %s %s~%s%s\n%s' % (
        line, mood, '\n'.join('· ' + w for w in want), days, start, end, ', ' + place if place else '',
        '\n'.join('· ' + e for e in extra))
    return text + ('\n\n' + join if join else '')


# 동호회 이름·종목·구·요일·시간은 DB 그대로다(이름으로 찾고 사전 확인한다). home = (구장 이름, 제목에 쓸 짧은 이름).
# play=False인 곳은 소개 글만 바꾼다. seed 가입 신청·매치에 쓰이거나 다른 파트 테스트 계정이 있는 곳,
# HY 님 시연 데이터 동호회(회원·경기·매칭방은 HY 님 데이터 그대로), 같은 종목 상대 동호회가 없어지는 곳(은평 풋살 클럽·관악 족구회).
EX_CLUBS = [
    dict(name='주말 풋살 클럽', sport='축구+풋살', district='성동구', days='2,4', start='19:00', end='21:00', play=False,
         intro=ex_intro('이름은 주말 풋살 클럽이지만 지금은 평일 저녁에 모이는 성동구 풋살팀입니다.',
                        '처음엔 주말에 뛰다가 다들 주말 일정이 많아져 화·목 저녁으로 옮겼어요. 오래 손발을 맞춰 온 멤버들이라 '
                        '패스 위주로 차분하게 경기합니다.',
                        ['화·목 저녁에 꾸준히 나올 수 있는 분', '처음이어도 괜찮아요, 천천히 손발을 맞춰 가요'],
                        '화·목', '19:00', '21:00', None, ['다른 동호회와 5:5 매치를 가끔 잡아요'], None)),
    dict(name='노원 농구회', sport='농구', district='노원구', days='6,7', start='09:00', end='11:00', created=date(2025, 3, 15),
         home=('노원 농구장', '노원 농구장'),
         intro=ex_intro('노원 농구장에서 주말 아침마다 모이는 농구 동호회입니다.',
                        '아침 9시에 모여 몸을 풀고 3:3 하프코트로 시작해서, 사람이 모이면 4:4로 다른 동호회와 경기를 합니다. '
                        '끝나고 같이 아침을 먹는 날이 많아요.',
                        ['주말 아침 일찍 나올 수 있는 분', '슛보다 패스를 먼저 보는 분'],
                        '토·일', '09:00', '11:00', '노원 농구장', ['한 달에 두 번 정도 다른 동호회와 교류전'],
                        '가입 신청에 농구 경력과 주 포지션을 적어 주세요.')),
    dict(name='송파 셔틀콕', sport='배드민턴', district='송파구', days='1,3', start='20:00', end='22:00', play=False,
         intro=ex_intro('송파구에서 월요일과 수요일 저녁에 모여 복식을 치는 배드민턴 동호회입니다.',
                        '초중급 회원이 섞여 있어 매번 실력이 비슷하게 짝을 나누고, 경기 사이사이 서로 자세를 봐 줍니다.',
                        ['지금은 모집을 마감했어요', '다시 모집할 때 이 글로 알릴게요'],
                        '월·수', '20:00', '22:00', None, ['셔틀콕은 동호회에서 같이 삽니다'], None)),
    dict(name='마포 테니스 클럽', sport='테니스', district='마포구', days='5', start='07:00', end='09:00', created=date(2025, 6, 5),
         home=('월드컵공원 테니스장', '월드컵공원 테니스장'),
         intro=ex_intro('금요일 아침 출근 전에 한 게임 치고 하루를 시작하는 테니스 동호회입니다.',
                        '월드컵공원 테니스장에서 7시에 모여 랠리로 몸을 풀고 복식 두세 게임을 칩니다. 8시 50분에는 정리하고 바로 출근해요.',
                        ['금요일 아침 7시에 나올 수 있는 분', '랠리를 10번 이상 이어 갈 수 있는 분'],
                        '금요일', '07:00', '09:00', '월드컵공원 테니스장', ['공은 돌아가며 준비합니다'],
                        '가입 신청에 구력과 출근 시간대를 적어 주세요.')),
    dict(name='서초 나이트 테니스', sport='테니스', district='서초구', days='2,4', start='21:00', end='23:00', created=date(2025, 7, 12),
         home=('서초 테니스클럽', '서초 테니스클럽'),
         intro=ex_intro('퇴근하고 밤 9시에 모여 복식을 치는 서초구 야간 테니스 동호회입니다.',
                        '조명 아래에서 치는 밤 테니스를 좋아하는 사람들이 모였어요. 늦은 시간이라 짧고 굵게 복식만 치고 11시에 끝냅니다.',
                        ['화·목 밤 시간이 비는 분', '복식 로테이션에 익숙한 분'],
                        '화·목', '21:00', '23:00', '서초 테니스클럽', ['다른 동호회와 복식 교류전: 한 달에 한두 번'],
                        '가입 신청에 구력과 주로 치는 위치를 적어 주세요.')),
    dict(name='성동 셔틀콕 모임', sport='배드민턴', district='성동구', days='3,6', start='19:00', end='21:00', created=date(2025, 8, 3),
         home=('중랑물재생센터 배드민턴장', '중랑물재생 배드민턴장'),
         intro=ex_intro('성동구 생활체육 배드민턴 모임입니다. 수요일과 토요일 저녁에 만나요.',
                        '중랑물재생센터 배드민턴장에서 복식 위주로 치고, 처음 온 분은 기존 회원과 짝이 되어 경기 흐름을 익힙니다.',
                        ['배드민턴을 다시 시작하고 싶은 분', '일주일에 한 번은 나올 수 있는 분'],
                        '수·토', '19:00', '21:00', '중랑물재생센터 배드민턴장', ['분기마다 동호회 안에서 작은 대회를 엽니다'],
                        '가입 신청에 배드민턴 경력을 간단히 적어 주세요.')),
    dict(name='광진 배드민턴회', sport='배드민턴', district='광진구', days='2,5', start='20:00', end='22:00', created=date(2025, 8, 24),
         home=('광진 배드민턴장', '광진 배드민턴장'),
         intro=ex_intro('광진구 주민들이 화요일과 금요일 저녁에 모이는 배드민턴 동호회입니다.',
                        '광진 배드민턴장 코트 두 면을 쓰며 복식을 칩니다. 오래 함께한 회원이 많아 분위기가 편하고, '
                        '경기 뒤에는 서로 잘된 점을 짧게 이야기해요.',
                        ['지금은 모집을 마감했어요', '자리가 나면 다시 모집할게요'],
                        '화·금', '20:00', '22:00', '광진 배드민턴장', ['다른 동호회와 복식 교류전: 한 달에 한두 번'], None)),
    dict(name='서초 테니스회', sport='테니스', district='서초구', days='1,4', start='19:30', end='21:30', created=date(2025, 9, 6),
         home=('서초 스포츠센터', '서초 스포츠센터'),
         intro=ex_intro('서초 스포츠센터에서 월요일과 목요일 저녁에 복식을 치는 테니스 동호회입니다.',
                        '잘 치는 것보다 꾸준히 치는 걸 중요하게 생각해요. 매번 짝을 바꿔 복식을 치고, 처음 오신 분도 첫날부터 경기에 들어갑니다.',
                        ['레슨을 3개월 이상 받아 본 분', '월·목 저녁에 꾸준히 나올 수 있는 분'],
                        '월·목', '19:30', '21:30', '서초 스포츠센터', ['코트비는 참석 인원끼리 나눠 냅니다'],
                        '가입 신청에 구력과 레슨 경험을 적어 주세요.')),
    dict(name='영등포 테니스 클럽', sport='테니스', district='영등포구', days='3,5', start='20:00', end='22:00', created=date(2025, 9, 21),
         home=('영등포 테니스장', '영등포 테니스장'),
         intro=ex_intro('영등포 테니스장에서 수요일과 금요일 저녁에 모이는 초중급 테니스 모임입니다.',
                        '레슨을 막 끝내고 실전이 필요한 분들이 많아요. 복식 경기 중에 포지션과 콜을 서로 알려 주며 칩니다.',
                        ['레슨은 받았는데 경기 경험이 적은 분', '실수해도 웃고 넘길 수 있는 분'],
                        '수·금', '20:00', '22:00', '영등포 테니스장', ['다른 동호회와 복식 교류전: 한 달에 한 번'],
                        '가입 신청에 구력을 적어 주세요.')),
    dict(name='송파 농구 동호회', sport='농구', district='송파구', days='6', start='10:00', end='12:00', created=date(2025, 10, 5),
         home=('광나루 농구장(올림픽대로 방향)', '광나루 농구장'),
         intro=ex_intro('토요일 오전 한강 바람 맞으며 농구하는 송파 지역 동호회입니다.',
                        '광나루 농구장(올림픽대로 방향)에서 3:3으로 몸을 풀고, 인원이 차면 4:4 경기를 합니다. '
                        '비가 오면 쉬고 단체 채팅방에 미리 알려요.',
                        ['토요일 오전에 꾸준히 나올 수 있는 분', '야외 코트 농구를 좋아하는 분'],
                        '토요일', '10:00', '12:00', '광나루 농구장', ['다른 동호회와 4:4 교류전: 한 달에 두 번'],
                        '가입 신청에 농구 경력과 주 포지션을 적어 주세요.')),
    dict(name='송파 배드민턴회', sport='배드민턴', district='송파구', days='7', start='09:00', end='12:00', created=date(2025, 10, 19),
         home=('송파 배드민턴홀', '송파 배드민턴홀'),
         intro=ex_intro('일요일 아침 송파 배드민턴홀에서 세 시간 동안 넉넉하게 치는 직장인 배드민턴 모임입니다.',
                        '평일에 못 친 만큼 일요일에 몰아서 칩니다. 복식 위주로 돌아가며 치고, 중간에 쉬는 시간도 충분히 둬요.',
                        ['평일엔 바쁜 직장인', '일요일 오전을 운동으로 시작하고 싶은 분'],
                        '일요일', '09:00', '12:00', '송파 배드민턴홀', ['한 달에 한 번 다른 동호회와 교류전'],
                        '가입 신청에 배드민턴 경력과 주로 치는 시간대를 적어 주세요.')),
    dict(name='은평 풋살 클럽', sport='축구+풋살', district='은평구', days='3,6', start='20:00', end='22:00', play=False,
         intro=ex_intro('은평 풋살아레나에서 수요일과 토요일 밤에 정기전을 여는 풋살 동호회입니다.',
                        '매번 두 팀으로 나눠 정기전을 하고, 한 달에 두세 번은 다른 동호회를 불러 5:5 경기를 합니다.',
                        ['은평구 근처에 살거나 일하는 분', '풋살화를 갖고 있는 분'],
                        '수·토', '20:00', '22:00', '은평 풋살아레나', ['구장비는 참석 인원끼리 나눠 냅니다'],
                        '가입 신청에 주 포지션을 적어 주세요.')),
    dict(name='광진 테니스회', sport='테니스', district='광진구', days='6,7', start='08:00', end='10:00', created=date(2025, 11, 16),
         home=('어린이대공원 테니스장 A코트', '어린이대공원 테니스장'),
         intro=ex_intro('주말 아침 어린이대공원 테니스장에서 복식을 치는 광진 지역 동호회입니다.',
                        '8시에 모여 랠리로 몸을 풀고, 실력별로 짝을 나눠 복식 서너 게임을 치고 10시에 마칩니다.',
                        ['주말 아침 일찍 일어나는 분', '복식 경기 규칙을 아는 분'],
                        '토·일', '08:00', '10:00', '어린이대공원 테니스장', ['다른 동호회와 복식 교류전: 한 달에 한두 번'],
                        '가입 신청에 구력과 주말 중 나올 수 있는 날을 적어 주세요.')),
    dict(name='강서 배드민턴회', sport='배드민턴', district='강서구', days='2,4', start='20:00', end='22:00', created=date(2025, 12, 7),
         home=('강서 배드민턴장', '강서 배드민턴장'),
         intro=ex_intro('강서 배드민턴장에서 화요일과 목요일 저녁에 모이는 배드민턴 동호회입니다. 초보도 환영해요.',
                        '처음 2주는 기본 스텝과 클리어를 같이 연습하고, 그다음부터 복식 경기에 들어갑니다. '
                        '라켓이 없으면 동호회 라켓을 빌려 드려요.',
                        ['배드민턴을 처음 배우거나 다시 시작하는 분', '화·목 저녁에 시간이 되는 분'],
                        '화·목', '20:00', '22:00', '강서 배드민턴장', ['다른 동호회와 복식 교류전: 한 달에 한 번'],
                        '가입 신청에 배드민턴 경험이 있는지 적어 주세요.')),
    dict(name='영등포 농구클럽', sport='농구', district='영등포구', days='5', start='19:00', end='21:00', created=date(2025, 12, 21),
         home=('영등포 농구센터', '영등포 농구센터'),
         intro=ex_intro('금요일 저녁 영등포 농구센터에서 한 주를 마무리하는 농구 동호회입니다.',
                        '4:4로 뛰어요. 오래 같이 뛴 회원이 많아 손발이 잘 맞고, 경기 뒤에는 근처에서 저녁을 먹습니다.',
                        ['지금은 모집을 마감했어요', '자리가 나면 다시 모집할게요'],
                        '금요일', '19:00', '21:00', '영등포 농구센터', ['다른 동호회와 4:4 교류전: 한 달에 두 번'], None)),
    dict(name='노원 풋살회', sport='축구+풋살', district='노원구', days='7', start='15:00', end='17:00', play=False,
         intro=ex_intro('노원구에서 일요일 오후에 공 차는 풋살 동호회입니다.',
                        '주말 오후 느긋하게 모여 5:5로 뛰어요. 경기보다 같이 땀 흘리는 시간을 더 좋아하는 사람들이 모였습니다.',
                        ['일요일 오후에 시간이 되는 분', '노원구 근처에 사는 분'],
                        '일요일', '15:00', '17:00', None, ['구장비는 참석 인원끼리 나눠 냅니다'],
                        '가입 신청에 간단한 자기소개를 남겨 주세요.')),
    dict(name='관악 족구회', sport='족구', district='관악구', days='6', start='09:00', end='11:00', play=False,
         intro=ex_intro('토요일 아침 관악 족구장에서 3:3 족구를 하는 지역 동호회입니다.',
                        '군대에서 족구를 처음 배운 분부터 대학 동아리 출신까지 모였어요. 서브와 토스 연습을 하고 나서 세트 경기를 합니다.',
                        ['족구 규칙을 아는 분', '토요일 아침에 꾸준히 나올 수 있는 분'],
                        '토요일', '09:00', '11:00', '관악 족구장', ['다른 동호회와 3:3 교류전: 한 달에 한두 번'],
                        '가입 신청에 족구 경험을 적어 주세요.')),
    dict(name='강서 테니스회', sport='테니스', district='강서구', days='3', start='20:00', end='22:00', created=date(2026, 3, 8),
         home=('강서 테니스장', '강서 테니스장'),
         intro=ex_intro('마곡·발산 직장인들이 수요일 저녁 강서 테니스장에 모이는 테니스 동호회입니다.',
                        '일주일의 한가운데에 한 번 크게 땀을 흘리자는 모임이에요. 복식 위주로 치고, 처음 오신 분은 기존 회원과 짝이 됩니다.',
                        ['마곡·발산·가양 근처에서 일하는 분', '복식 경기를 해 본 분'],
                        '수요일', '20:00', '22:00', '강서 테니스장', ['다른 동호회와 복식 교류전: 한 달에 한 번'],
                        '가입 신청에 구력과 일하는 동네를 적어 주세요.')),
    dict(name='송파 배드민턴', sport='배드민턴', district='송파구', days='6,7', start='09:00', end='11:00', created=date(2026, 5, 17),
         home=('송파 배드민턴코트', '송파 배드민턴코트'),
         intro=ex_intro('주말 아침 송파 배드민턴코트에서 복식을 치는 배드민턴 모임입니다.',
                        '석촌호수 산책을 하다 모인 이웃들이 시작한 모임이에요. 아침 9시에 모여 두 시간 동안 짝을 바꿔 가며 칩니다.',
                        ['주말 아침에 운동하고 싶은 분', '송파·잠실 근처에 사는 분'],
                        '토·일', '09:00', '11:00', '송파 배드민턴코트', ['다른 동호회와 복식 교류전: 한 달에 한 번'],
                        '가입 신청에 배드민턴 경력을 적어 주세요.')),
    dict(name='잠실 농구회', sport='농구', district='송파구', days='1,3', start='20:00', end='22:00', play=False,
         intro=ex_intro('잠실에서 월요일과 수요일 저녁에 모이는 농구 동호회입니다.',
                        '퇴근 후 가볍게 슈팅 연습을 하고 3:3, 4:4로 경기를 합니다.',
                        ['지금은 모집을 마감했어요', '다시 모집할 때 이 글로 알릴게요'],
                        '월·수', '20:00', '22:00', None, ['다른 동호회와 교류전은 인원이 모이면 잡아요'], None)),
    dict(name='뚝섬 FC', sport='축구+풋살', district='광진구', days='6,7', start='10:00', end='12:00', play=False,
         intro=ex_intro('뚝섬한강공원 축구장에서 주말 오전에 뛰는 광진구 풋살팀 뚝섬 FC입니다.',
                        '한강을 옆에 두고 5:5로 뛰는 맛에 모인 팀이에요. 초보도 환영하고, 실력이 섞이도록 매번 팀을 다시 나눕니다.',
                        ['주말 오전에 한 번 이상 나올 수 있는 분', '초보부터 중급까지 누구나'],
                        '토·일', '10:00', '12:00', '뚝섬한강공원 축구장 2', ['다른 동호회와 5:5 매치: 한 달에 두세 번'],
                        '가입 신청에 주 포지션과 풋살 경력을 적어 주세요.')),
    dict(name='망원 풋살 크루', sport='축구+풋살', district='마포구', days='2,4', start='19:00', end='21:00', play=False,
         intro=ex_intro('화요일과 목요일 저녁 망원유수지 풋살장에서 모이는 마포구 풋살 크루입니다.',
                        '합정·망원·상수에서 일하는 사람들이 퇴근길에 들러 뛰고 가요. 초보도 환영하고, 처음 오신 분은 수비부터 같이 맞춰 봅니다.',
                        ['평일 저녁 7시에 올 수 있는 분', '같이 뛰고 같이 치우는 분'],
                        '화·목', '19:00', '21:00', '망원유수지 풋살장', ['다른 동호회와 5:5 매치: 한 달에 두세 번'],
                        '가입 신청에 주 포지션을 적어 주세요.')),
    dict(name='잠실 풋살 클럽', sport='축구+풋살', district='송파구', days='6', start='14:00', end='16:00', play=False,
         intro=ex_intro('토요일 오후 잠실종합운동장 제2풋살경기장에서 뛰는 송파구 풋살 클럽입니다.',
                        '점심 먹고 느긋하게 모여 두 시간 동안 5:5 경기를 합니다. 초보도 환영하고, 실력에 맞춰 팀을 나눠요.',
                        ['토요일 오후에 시간이 되는 분', '송파구 근처에 사는 분'],
                        '토요일', '14:00', '16:00', '잠실종합운동장 제2풋살경기장', ['다른 동호회와 5:5 매치: 한 달에 두 번'],
                        '가입 신청에 간단한 자기소개를 남겨 주세요.')),
    dict(name='반포 농구단', sport='농구', district='서초구', days='3,6', start='18:00', end='20:00', play=False,
         intro=ex_intro('반포한강공원 농구장에서 해 질 무렵 농구하는 서초구 동호회입니다.',
                        '수요일과 토요일 저녁 6시에 모여 4:4로 뜁니다. 초보도 환영하고, 처음 오신 분은 3:3 하프코트부터 같이 해요.',
                        ['저녁 6시에 올 수 있는 분', '야외 코트를 좋아하는 분'],
                        '수·토', '18:00', '20:00', '반포한강공원 농구장', ['다른 동호회와 4:4 교류전: 한 달에 두세 번'],
                        '가입 신청에 농구 경력과 주 포지션을 적어 주세요.')),
    dict(name='광나루 바스켓', sport='농구', district='강동구', days='7', start='14:00', end='16:00', play=False,
         intro=ex_intro('일요일 오후 광나루한강공원 농구장에서 뛰는 강동구 농구 동호회입니다.',
                        '천호·암사 이웃들이 모여 만든 팀이에요. 4:4 경기를 주로 하고, 초보도 환영해서 경기마다 실력을 섞어 팀을 나눕니다.',
                        ['일요일 오후에 시간이 되는 분', '강동구 근처에 사는 분'],
                        '일요일', '14:00', '16:00', '광나루한강공원 농구장', ['다른 동호회와 4:4 교류전: 한 달에 두 번'],
                        '가입 신청에 농구 경력을 적어 주세요.')),
    dict(name='한강 족구회', sport='족구', district='광진구', days='6,7', start='10:00', end='12:00', play=False,
         intro=ex_intro('주말 오전 뚝섬한강공원 족구장에서 3:3 족구를 하는 광진구 동호회입니다.',
                        '족구를 처음 하는 분도 환영해요. 서브 받는 법부터 같이 연습하고, 몸이 풀리면 세트 경기를 합니다.',
                        ['주말 오전에 시간이 되는 분', '족구를 해 보고 싶었던 분'],
                        '토·일', '10:00', '12:00', '뚝섬한강공원 족구장', ['다른 동호회와 3:3 교류전: 한 달에 한두 번'],
                        '가입 신청에 간단한 자기소개를 남겨 주세요.')),
    dict(name='뚝섬 셔틀콕', sport='배드민턴', district='광진구', days='2,5', start='18:00', end='20:00', play=False,
         intro=ex_intro('화요일과 금요일 저녁 뚝섬한강공원 배드민턴장에서 치는 광진구 배드민턴 동호회입니다.',
                        '바람이 약한 저녁 시간에 야외 코트에서 복식을 칩니다. 초보도 환영하고, 셔틀콕은 동호회에서 준비해요.',
                        ['퇴근 후 6시에 올 수 있는 분', '야외에서 운동하는 걸 좋아하는 분'],
                        '화·금', '18:00', '20:00', '뚝섬한강공원 배드민턴장', ['다른 동호회와 복식 교류전: 한 달에 한두 번'],
                        '가입 신청에 배드민턴 경력을 적어 주세요.')),
    dict(name='마포 랠리 테니스', sport='테니스', district='마포구', days='6', start='10:00', end='12:00', play=False,
         intro=ex_intro('토요일 오전 망원나들목 테니스장에서 랠리와 복식을 즐기는 마포구 테니스 동호회입니다.',
                        '이름처럼 랠리를 길게 이어 가는 걸 좋아해요. 앞 한 시간은 랠리, 뒤 한 시간은 복식 경기를 합니다. 초보도 환영해요.',
                        ['토요일 오전에 시간이 되는 분', '랠리를 오래 치고 싶은 분'],
                        '토요일', '10:00', '12:00', '망원나들목 테니스장', ['다른 동호회와 복식 교류전: 한 달에 한 번'],
                        '가입 신청에 구력을 적어 주세요.')),
]


def ex_schedule(rng, clubs):
    """한 종목 동호회끼리 지난 경기 날짜를 정한다. 홈 동호회의 활동 요일·시간·구장에서 한다(schedule과 같은 방식)."""
    n = len(clubs)
    remaining = [c['games'] for c in clubs]
    last_day = [None] * n
    meetings = [[0] * n for _ in range(n)]
    active_from = [max(EX_MATCH_FROM, c['created'] + timedelta(days=21)) for c in clubs]
    matches = []
    day = EX_MATCH_FROM
    while day <= EX_MATCH_UNTIL:
        def ahead(i):
            span = (EX_MATCH_UNTIL - active_from[i]).days + 1
            played = clubs[i]['games'] - remaining[i]
            return played > clubs[i]['games'] * ((day - active_from[i]).days + 1) / span + 0.5

        def free(i):
            return (remaining[i] > 0 and day >= active_from[i] and not ahead(i)
                    and (last_day[i] is None or (day - last_day[i]).days >= MIN_GAP_DAYS))

        def urgency(i):
            return remaining[i] / max(1.0, (EX_MATCH_UNTIL - max(day, active_from[i])).days / MIN_GAP_DAYS + 1)
        hosts = [i for i in range(n) if str(day.isoweekday()) in clubs[i]['days'].split(',') and free(i)]
        rng.shuffle(hosts)
        for h in hosts:
            if not free(h):
                continue
            others = [o for o in range(n) if o != h and free(o)]
            if not others:
                continue
            if rng.random() > min(1.0, 1.5 * max(urgency(h), max(urgency(o) for o in others))):
                continue
            weights = [urgency(o) ** 3 / (1 + 2 * meetings[h][o]) for o in others]
            o = rng.choices(others, weights)[0]
            matches.append({'home': clubs[h]['i'], 'away': clubs[o]['i'], 'day': day})
            for i in (h, o):
                remaining[i] -= 1
                last_day[i] = day
            meetings[h][o] += 1
            meetings[o][h] += 1
        day += timedelta(days=1)
    return matches if not any(remaining) else None


def ex_build(rng, used_names):
    """새 회원 → 지난 경기(출전·결과·레이팅) → 예정 매칭방과 팀 신청(대기). 조건에 맞지 않으면 None."""
    play = [c for c in EX_CLUBS if c.get('play', True)]
    for i, c in enumerate(play):
        c['i'] = i
        c['games'] = EX_GAMES if c['sport'] != '족구' else EX_GAMES - 1
        c['strength'] = rng.uniform(0.3, 0.8)
    matches = []
    for sport in TEAM_SIZE:
        pool = [c for c in play if c['sport'] == sport]
        found = ex_schedule(rng, pool)
        if found is None:
            return None
        matches += found
    matches.sort(key=lambda m: (m['day'], hm(play[m['home']]['start']), m['home']))
    names = set(used_names)

    def nickname():
        while True:
            name = rng.choice(SURNAMES) + rng.choice(GIVEN)
            if name not in names:
                names.add(name)
                return name

    first_day = {}
    for m in matches:
        for i in (m['home'], m['away']):
            first_day.setdefault(i, m['day'])
    users, members_of = [], []
    for c in play:
        # 새 회원은 창단 뒤(늦어도 2026-05 이후)부터 첫 경기 4일 전까지 가입한다
        lo = max(c['created'] + timedelta(days=1), date(2026, 5, 1))
        hi = first_day[c['i']] - timedelta(days=4)
        if hi < lo:
            return None
        ids = []
        joined = sorted(datetime.combine(lo + timedelta(days=rng.randint(0, (hi - lo).days)),
                                         time(rng.randint(9, 22), rng.choice([0, 30]))) for _ in range(NEW_MEMBERS[c['sport']]))
        for j in joined:
            no = len(users) + 1
            users.append({'no': no, 'email': 'club-user-%03d@spoit.test' % no, 'nickname': nickname(),
                          'district': c['district'], 'club': c['i'], 'sport': c['sport'],
                          'phone': '010-87%02d-%04d' % (no % 100, rng.randint(1000, 9999)),
                          'created_at': j - timedelta(days=rng.randint(1, 30), hours=rng.randint(0, 8)), 'joined_at': j,
                          'mmr': START_MMR, 'count': 0, 'available': j, 'rated_at': None})
            ids.append(no)
        members_of.append(ids)

    for m in matches:
        h, a = m['home'], m['away']
        hc = play[h]
        size = TEAM_SIZE[hc['sport']]
        start = datetime.combine(m['day'], hm(hc['start']))
        end = datetime.combine(m['day'], hm(hc['end']))
        m.update(start=start, end=end, facility=hc['home'], sport=hc['sport'], size=size, title='%s %s요일 %s %d:%d' % (
            hc['home'][1], WEEKDAY[m['day'].isoweekday() - 1], slot_label(hc['start']), size, size))
        sides = []
        for side, ci in (('A', h), ('B', a)):
            lead = start - timedelta(days=rng.randint(2, 4), hours=rng.randint(0, 10))
            ids = members_of[ci]
            eligible = [u for u in ids if users[u - 1]['joined_at'] < lead - timedelta(hours=1)]
            if len(eligible) < size:
                return None
            # 먼저 가입한 몇 명은 매번 나오는 고정 멤버(배치를 마친다), 나머지는 덜 뛴 사람부터
            core = [u for u in ids[:REGULARS[hc['sport']]] if u in eligible]
            rest = [u for u in eligible if u not in core]
            rng.shuffle(rest)
            chosen = core + sorted(rest, key=lambda u: users[u - 1]['count'])[:size - len(core)]
            created = max([lead] + [users[u - 1]['available'] for u in chosen])
            if created > start - timedelta(hours=6):
                return None
            rows = []
            for u in chosen:
                confirmed = created + timedelta(minutes=rng.randint(60, 180))
                rows.append({'user': u, 'club': ci, 'side': side, 'created': created, 'confirmed': confirmed,
                             'entry': users[u - 1]['mmr']})
            sides.append(rows)
        m['apps'] = sides[0] + sides[1]
        parts = sorted(m['apps'], key=lambda r: (r['confirmed'], r['user']))
        for pid, r in enumerate(parts, 1):
            r['pid'] = pid
        for side in 'AB':
            team = [r for r in m['apps'] if r['side'] == side]
            m['captain_' + side] = min(team, key=lambda r: (-r['entry'], r['pid']))['pid']
        sa = play[h]['strength'] - play[a]['strength']
        r = rng.random()
        if r < DRAW_RATE:
            m['result'] = 'DRAW'
        else:
            p_home = 1 / (1 + 10 ** (-sa / 0.35))
            m['result'] = 'A_WIN' if (r - DRAW_RATE) / (1 - DRAW_RATE) < p_home else 'B_WIN'
        m['sub_A'] = end + timedelta(minutes=rng.randint(20, 90))
        m['sub_B'] = end + timedelta(minutes=rng.randint(30, 180))
        m['result_at'] = max(m['sub_A'], m['sub_B'])
        m['rated_at'] = end + timedelta(hours=24)
        avg = {s: team_average([r['entry'] for r in m['apps'] if r['side'] == s]) for s in 'AB'}
        exp = {'A': expected_score(avg['A'], avg['B']), 'B': expected_score(avg['B'], avg['A'])}
        m['avg'] = avg
        m['history'] = []
        for r in sorted(m['apps'], key=lambda r: r['user']):
            u = users[r['user'] - 1]
            actual = Decimal('0.5') if m['result'] == 'DRAW' else (
                Decimal('1') if m['result'] == r['side'] + '_WIN' else Decimal('0'))
            delta = elo_delta(k_factor(u['count']), actual, exp[r['side']])
            before = u['mmr']
            u['mmr'] = before + delta
            u['count'] += 1
            u['rated_at'] = m['rated_at']
            u['available'] = m['rated_at'] + timedelta(hours=rng.randint(1, 6))
            m['history'].append({'pid': r['pid'], 'user': r['user'], 'before': before, 'after': u['mmr'],
                                 'delta': delta, 'actual': actual, 'expected': exp[r['side']]})

    # 예정 일정: 동호회마다 11/24~12/6 활동 시간에 매칭방 1개와 팀 신청(대기)
    rooms = []
    for c in play:
        days = [EX_ROOM_FROM + timedelta(days=k) for k in range((EX_ROOM_UNTIL - EX_ROOM_FROM).days + 1)]
        days = [d for d in days if str(d.isoweekday()) in c['days'].split(',') and not any(
            s < datetime.combine(d, hm(c['end'])) and e > datetime.combine(d, hm(c['start'])) for s, e in EX_BUSY.get(c['home'][0], []))]
        d = rng.choice(days)
        size = TEAM_SIZE[c['sport']]
        ids = members_of[c['i']]
        team = ids[:REGULARS[c['sport']]] + sorted(ids[REGULARS[c['sport']]:], key=lambda u: rng.random())
        rooms.append({'club': c['i'], 'day': d, 'start': datetime.combine(d, hm(c['start'])), 'end': datetime.combine(d, hm(c['end'])),
                      'facility': c['home'], 'sport': c['sport'], 'size': size, 'team': team[:size],
                      'title': '%s %s요일 %s %d:%d' % (c['home'][1], WEEKDAY[d.isoweekday() - 1], slot_label(c['start']), size, size)})
    return play, matches, users, members_of, rooms


def ex_acceptable(result):
    """모든 동호회가 이기고 진 경기가 있고, 배치를 마친 새 회원이 있고, 10월 중순 이후에도 경기가 있다."""
    if result is None:
        return False
    play, matches, users, members_of, rooms = result
    for c in play:
        ci = c['i']
        played = [m for m in matches if ci in (m['home'], m['away'])]
        wins = sum(1 for m in played if m['result'] == ('A_WIN' if m['home'] == ci else 'B_WIN'))
        losses = sum(1 for m in played if m['result'] == ('B_WIN' if m['home'] == ci else 'A_WIN'))
        if wins == 0 or losses == 0:
            return False
        if not any(users[u - 1]['count'] >= PLACEMENT_MATCHES for u in members_of[ci]):
            return False
        if max(m['day'] for m in played) < date(2026, 10, 15):
            return False
    return True


def ex_find(used_names):
    for attempt in range(200000):
        rng = random.Random(EX_SEED + attempt)
        result = ex_build(rng, used_names)
        if ex_acceptable(result):
            return rng, result, attempt
    raise SystemExit('기존 동호회 일정을 찾지 못했습니다.')


def ex_union(rows, columns):
    return union(rows, columns, where='@cx_ready = 1')


def main_existing(used_names):
    rng, (play, matches, users, members_of, rooms), attempt = ex_find(used_names)
    receipt = receipt_maker(rng)
    for c in EX_CLUBS:
        assert len(c['intro']) <= 500, c['name']
    facilities = []
    for c in play:
        key = (c['district'], c['home'][0])
        if key not in facilities:
            facilities.append(key)
    fac_var = {f[1]: '@cx_f%d' % (i + 1) for i, f in enumerate(facilities)}
    club_var = {c['name']: '@cx_c%d' % (i + 1) for i, c in enumerate(EX_CLUBS)}
    pv = {c['i']: club_var[c['name']] for c in play}
    for m in matches:
        for r in m['apps']:
            r['receipt'] = receipt(r['created'])
    app_no = part_no = 0
    groups = []
    for mi, m in enumerate(matches, 1):
        m['no'] = mi
        base = part_no
        for r in m['apps']:
            app_no += 1
            r['app'] = app_no
        for side in 'AB':
            team = [r['app'] for r in m['apps'] if r['side'] == side]
            groups.append((team[0], team[-1]))
        for r in m['apps']:
            r['participant'] = base + r['pid']
        part_no += len(m['apps'])
    waiting = []
    for k, room in enumerate(rooms):
        room['no'] = len(matches) + k + 1
        applied = EX_APPLIED_AT + timedelta(minutes=7 * k)
        first = app_no + 1
        for u in room['team']:
            app_no += 1
            waiting.append({'app': app_no, 'room': room['no'], 'user': u, 'club': room['club'], 'receipt': receipt(applied),
                            'created': applied})
        groups.append((first, app_no))
    receipts = [r['receipt'] for m in matches for r in m['apps']] + [w['receipt'] for w in waiting]
    assert len(set(receipts)) == len(receipts)
    history_rows = sum(len(m['history']) for m in matches)

    out = []
    w = out.append
    w('-- =====================================================================\n')
    w('-- 기존 동호회 데이터: seed 동호회 %d곳에 새 회원 %d명, 지난 동호회 경기 %d개(레이팅 이력 %d행),\n'
      '--                   예정 매칭방 %d개와 팀 신청 %d행을 넣고, seed·HY 님 데이터 동호회 %d곳의 소개 글을 다시 쓴다.\n'
      % (len(play), len(users), len(matches), history_rows, len(rooms), len(waiting), len(EX_CLUBS)))
    w('-- 이 파일은 scripts/club-demo-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.\n')
    w('-- 대상: 20261112-club-demo-data.sql까지 적용한 DB (로컬·운영)\n')
    w('-- 내용\n')
    w('--   * 기존 회원(seed 계정, demo-NNN)은 바꾸지 않는다. 새 회원(club-user-NNN@spoit.test, 비밀번호 spoit1234)만 동호회원으로 넣고,\n'
      '--     지난 경기는 새 회원끼리 같은 종목 동호회와 치른 것이다(농구 4:4, 배드민턴·테니스 복식 2:2).\n')
    w('--   * 지난 경기는 2026-08~11월 초, 홈 동호회의 구장·활동 시간. 팀 신청 확정 → 참가자 → 두 팀장 결과 제출 → 결과 마감(종료 24시간 뒤)에\n'
      '--     평가 없이 레이팅 반영까지 끝난 종료 상태다. 레이팅은 RatingCalculator와 같은 식으로 1000부터 시간순 계산했다.\n')
    w('--   * 예정 일정: 새 회원을 넣은 동호회마다 11/24~12/6 활동 시간에 매칭방 1개와 팀 신청(대기).\n')
    w('--   * 소개 글만 바꾸는 곳: %s.\n'
      '--     seed 가입 신청·매치에 쓰이거나 다른 파트 테스트 계정이 있는 곳, HY 님 시연 데이터 동호회(회원·경기·매칭방은 그대로),\n'
      '--     같은 종목 상대 동호회가 없는 곳이다.\n'
      % ', '.join(c['name'] for c in EX_CLUBS if not c.get('play', True)))
    w('-- 원칙\n')
    w('--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.\n')
    w('--   * %s가 이미 있으면 적용된 것으로 보고 아무것도 넣지 않는다(오류 없음).\n' % EX_MARKER)
    w('--   * 동호회·종목·지역·시설·담당자는 이름으로 찾는다. 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다.\n')
    w('--     기존 행은 동호회 소개 글만 바꾼다.\n')
    w('-- =====================================================================\n\n')
    w('SET NAMES utf8mb4;\nSTART TRANSACTION;\n\n')

    w('-- 0. 이름으로 번호 찾기\n')
    for name, var in EX_SPORT_VAR.items():
        w("SET %s = (SELECT MAX(sport_id) FROM sport WHERE name = %s);\n" % (var, q(name)))
    districts = sorted({c['district'] for c in EX_CLUBS})
    for d in districts:
        w("SET @cx_r_%s = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = %s);\n" % (d, q(d)))
    for c in EX_CLUBS:
        w("SET %s = (SELECT MAX(c.club_id) FROM club c JOIN club_region cr ON cr.club_id = c.club_id "
          "WHERE c.active_club_name = %s AND c.sport_id = %s AND cr.region_id = @cx_r_%s);\n"
          % (club_var[c['name']], q(c['name']), EX_SPORT_VAR[c['sport']], c['district']))
    for gu, name in facilities:
        v = fac_var[name]
        w("SET %s = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 "
          "WHERE r.name = %s AND f.name = %s AND f.deleted_at IS NULL);\n" % (v, q(gu), q(name)))
        w("SET %s_mgr = (SELECT MIN(user_id) FROM facility_manager WHERE facility_id = %s);\n" % (v, v))
    w('\n')

    slots = [(fac_var[m['facility'][0]], m['start'], m['end']) for m in matches]
    slots += [(fac_var[r['facility'][0]], r['start'], r['end']) for r in rooms]
    emails = [u['email'] for u in users if u['email'] != EX_MARKER]
    w('-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.\n')
    w('SELECT (%s),\n' % ' AND '.join('%s IS NOT NULL' % v for v in EX_SPORT_VAR.values()))
    w('       (%s) = %d,\n' % (' + '.join('(@cx_r_%s IS NOT NULL)' % d for d in districts), len(districts)))
    w('       (%s) = %d,\n' % (' + '.join('(%s IS NOT NULL)' % club_var[c['name']] for c in EX_CLUBS), len(EX_CLUBS)))
    w('       (%s) = %d,\n' % (' + '.join(
        '((SELECT COUNT(*) FROM club_member WHERE club_id = %s AND left_at IS NULL) + %d < (SELECT max_members FROM club WHERE club_id = %s))'
        % (pv[c['i']], NEW_MEMBERS[c['sport']], pv[c['i']]) for c in play), len(play)))
    w('       (%s) = %d,\n' % (' + '.join(
        '(%s IS NOT NULL AND %s_mgr IS NOT NULL AND EXISTS (SELECT 1 FROM facility_sport WHERE facility_id = %s AND sport_id = %s))'
        % (fac_var[c['home'][0]], fac_var[c['home'][0]], fac_var[c['home'][0]], EX_SPORT_VAR[c['sport']]) for c in play), len(play)))
    w("       NOT EXISTS (SELECT 1 FROM social_match WHERE status <> '취소' AND (\n%s)),\n" % ' OR\n'.join(
        '         (facility_id = %s AND start_at < %s AND end_at > %s)' % (v, q(dt(e)), q(dt(s))) for v, s, e in slots))
    w('       NOT EXISTS (SELECT 1 FROM match_application WHERE receipt_no IN (%s)),\n' % ', '.join(q(r) for r in receipts))
    w('       NOT EXISTS (SELECT 1 FROM app_user WHERE email IN (%s)),\n' % ', '.join(q(e) for e in emails))
    w('       EXISTS (SELECT 1 FROM app_user WHERE email = %s)\n' % q(EX_MARKER))
    w('  INTO @cx_sport, @cx_districts, @cx_clubs, @cx_capacity, @cx_facilities, @cx_slots_free, @cx_receipts_free, '
      '@cx_emails_free, @cx_already_applied;\n')
    w('SET @cx_checks = @cx_sport AND @cx_districts AND @cx_clubs AND @cx_capacity AND @cx_facilities AND @cx_slots_free '
      'AND @cx_receipts_free AND @cx_emails_free;\n')
    w('SET @cx_ready = @cx_checks AND NOT @cx_already_applied;\n')
    w('SELECT @cx_sport AS sport, @cx_districts AS districts, @cx_clubs AS clubs, @cx_capacity AS capacity, @cx_facilities AS facilities,\n'
      '       @cx_slots_free AS slots_free, @cx_receipts_free AS receipts_free, @cx_emails_free AS emails_free,\n'
      '       @cx_already_applied AS already_applied;\n')
    w('-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.\n')
    w('DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @cx_checks = 0 AND @cx_already_applied = 0);\n\n')

    w('SELECT MAX(user_id), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),\n'
      '       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),\n'
      '       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant)\n'
      '  INTO @cx_u, @cx_m, @cx_sm, @cx_a, @cx_p\n  FROM app_user;\n\n')

    w('-- 1. 새 회원 (비밀번호 spoit1234)\n')
    w('INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)\n')
    w(ex_union([['(@cx_u + %d)' % u['no'], q(u['email']), q(PASSWORD_HASH), q(u['nickname']), q(u['phone']), q(ROLE),
                 q(dt(u['created_at']))] for u in users],
               ['user_id', 'email', 'password_hash', 'nickname', 'phone', 'role', 'created_at']))
    w('\nINSERT INTO user_region (user_id, region_id)\n')
    w(ex_union([['(@cx_u + %d)' % u['no'], '@cx_r_%s' % u['district']] for u in users], ['user_id', 'region_id']))
    w('\nINSERT INTO user_preferred_sport (user_id, sport_id)\n')
    w(ex_union([['(@cx_u + %d)' % u['no'], EX_SPORT_VAR[u['sport']]] for u in users], ['user_id', 'sport_id']))
    w('\n-- 종목별 레이팅 = 마지막 레이팅 이력. rated_match_count가 5 이상이면 배치 완료로 팀 등급 평균에 들어간다.\n')
    w('INSERT INTO sport_rating (user_id, sport_id, current_mmr, rated_match_count, updated_at)\n')
    w(ex_union([['(@cx_u + %d)' % u['no'], EX_SPORT_VAR[u['sport']], str(u['mmr']), str(u['count']),
                 q(dt(u['rated_at'] or u['joined_at']))] for u in users],
               ['user_id', 'sport_id', 'current_mmr', 'rated_match_count', 'updated_at']))
    w('\n-- 2. 동호회원 (기존 동호회에 새 회원이 가입)\n')
    w('INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at)\n')
    w(ex_union([['(@cx_m + %d)' % u['no'], pv[u['club']], '(@cx_u + %d)' % u['no'], q('동호회원'), q(dt(u['joined_at']))]
                for u in users], ['club_member_id', 'club_id', 'user_id', 'role', 'joined_at']))
    w('\n-- 소개 글 (시연 데이터와 같은 형식)\n')
    w('UPDATE club\n   SET intro = CASE club_id\n%s\n       END\n' % '\n'.join(
        '         WHEN %s THEN %s' % (club_var[c['name']], q(c['intro'])) for c in EX_CLUBS))
    w(' WHERE @cx_ready = 1 AND club_id IN (%s);\n' % ', '.join(club_var[c['name']] for c in EX_CLUBS))

    w('\n-- 3. 경기: 지난 동호회 경기(종료, 결과 확정, 레이팅 반영) + 예정 매칭방(모집 중). 팀장은 참가자를 넣은 뒤 지정한다.\n')
    w('INSERT INTO social_match (match_id, facility_id, sport_id, created_by, title, start_at, end_at, min_participants, max_participants, '
      'team_a_capacity, team_b_capacity, fee_per_person, required_skill, status, photo_url, assignment_method, assigned_by, '
      'assignment_confirmed_at, result_status, final_result, result_confirmed_at, rating_status, rating_processed_at)\n')
    rows = []
    for m in matches:
        v = fac_var[m['facility'][0]]
        n = str(m['size'] * 2)
        rows.append(['(@cx_sm + %d)' % m['no'], v, EX_SPORT_VAR[m['sport']], v + '_mgr', q(m['title']), q(dt(m['start'])),
                     q(dt(m['end'])), n, n, str(m['size']), str(m['size']), '0', q('중급'), q('종료'),
                     q('/images/matches/%s.jpg' % SPORT_PHOTO[m['sport']]), q('MANUAL'), v + '_mgr',
                     q(dt(m['start'] - timedelta(hours=1))), q('CONFIRMED'), q(m['result']), q(dt(m['result_at'])),
                     q('APPLIED'), q(dt(m['rated_at']))])
    for r in rooms:
        v = fac_var[r['facility'][0]]
        n = str(r['size'] * 2)
        rows.append(['(@cx_sm + %d)' % r['no'], v, EX_SPORT_VAR[r['sport']], v + '_mgr', q(r['title']), q(dt(r['start'])),
                     q(dt(r['end'])), n, n, str(r['size']), str(r['size']), '0', q('중급'), q('모집 중'),
                     q('/images/matches/%s.jpg' % SPORT_PHOTO[r['sport']]), q('MANUAL'), 'NULL', 'NULL', 'NULL', 'NULL',
                     'NULL', q('WAITING'), 'NULL'])
    w(ex_union(rows, ['match_id', 'facility_id', 'sport_id', 'created_by', 'title', 'start_at', 'end_at', 'min_participants',
                      'max_participants', 'team_a_capacity', 'team_b_capacity', 'fee_per_person', 'required_skill', 'status',
                      'photo_url', 'assignment_method', 'assigned_by', 'assignment_confirmed_at', 'result_status', 'final_result',
                      'result_confirmed_at', 'rating_status', 'rating_processed_at']))

    w('\n-- 4. 팀 신청: 동호회마다 대표 1명 + 팀원. 지난 경기는 시설 담당자가 확정, 예정 매칭방은 대기. 행을 넣은 뒤 묶음 번호를 붙인다.\n')
    w('INSERT INTO match_application (application_id, match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at, confirmed_at)\n')
    rows = [['(@cx_a + %d)' % r['app'], '(@cx_sm + %d)' % m['no'], '(@cx_u + %d)' % r['user'], pv[r['club']],
             q('동호회'), q('확정'), q(r['receipt']), q(dt(r['created'])), q(dt(r['confirmed']))] for m in matches for r in m['apps']]
    rows += [['(@cx_a + %d)' % a['app'], '(@cx_sm + %d)' % a['room'], '(@cx_u + %d)' % a['user'], pv[a['club']],
              q('동호회'), q('대기'), q(a['receipt']), q(dt(a['created'])), 'NULL'] for a in waiting]
    w(ex_union(rows, ['application_id', 'match_id', 'applicant_user_id', 'club_id', 'application_type', 'status', 'receipt_no',
                      'created_at', 'confirmed_at']))
    cases = '\n'.join('         WHEN application_id BETWEEN (@cx_a + %d) AND (@cx_a + %d) THEN (@cx_a + %d)' % (f, l, f) for f, l in groups)
    w('UPDATE match_application\n   SET group_application_id = CASE\n%s\n       END\n' % cases)
    w(' WHERE @cx_ready = 1 AND application_id BETWEEN (@cx_a + 1) AND (@cx_a + %d);\n\n' % app_no)

    w('-- 5. 참가자 (확정 순서대로 번호, entry_mmr은 확정 시점의 레이팅 = 직전 경기 반영 뒤 값)\n')
    w('INSERT INTO match_participant (participant_id, application_id, match_id, user_id, participation_status, match_side, entry_mmr, joined_at)\n')
    rows = []
    for m in matches:
        for r in sorted(m['apps'], key=lambda r: r['pid']):
            rows.append(['(@cx_p + %d)' % r['participant'], '(@cx_a + %d)' % r['app'], '(@cx_sm + %d)' % m['no'],
                         '(@cx_u + %d)' % r['user'], q('확정'), q(r['side']), str(r['entry']), q(dt(r['confirmed']))])
    w(ex_union(rows, ['participant_id', 'application_id', 'match_id', 'user_id', 'participation_status', 'match_side',
                      'entry_mmr', 'joined_at']))
    w('-- 팀장: 팀에서 entry_mmr이 가장 높은 참가자(같으면 번호가 작은 쪽, TeamCaptainSelector)\n')
    base = {m['no']: m['apps'][0]['participant'] - m['apps'][0]['pid'] for m in matches}
    w('UPDATE social_match\n   SET captain_a_participant_id = CASE match_id\n%s\n       END,\n'
      % '\n'.join('         WHEN (@cx_sm + %d) THEN (@cx_p + %d)' % (m['no'], base[m['no']] + m['captain_A']) for m in matches))
    w('       captain_b_participant_id = CASE match_id\n%s\n       END\n'
      % '\n'.join('         WHEN (@cx_sm + %d) THEN (@cx_p + %d)' % (m['no'], base[m['no']] + m['captain_B']) for m in matches))
    w(' WHERE @cx_ready = 1 AND match_id BETWEEN (@cx_sm + 1) AND (@cx_sm + %d);\n\n' % len(matches))

    w('-- 6. 두 팀장의 결과 제출 (같은 결과라 확정)\n')
    w('INSERT INTO match_result_submission (match_id, captain_participant_id, reported_result, submission_status, submitted_at)\n')
    rows = []
    for m in matches:
        for side in 'AB':
            rows.append(['(@cx_sm + %d)' % m['no'], '(@cx_p + %d)' % (base[m['no']] + m['captain_' + side]), q(m['result']),
                         q('CONFIRMED'), q(dt(m['sub_' + side]))])
    w(ex_union(rows, ['match_id', 'captain_participant_id', 'reported_result', 'submission_status', 'submitted_at']))

    w('\n-- 7. 레이팅 이력 (평가 없이 반영: evaluation_delta 0, evaluation_score NULL)\n')
    w('INSERT INTO sport_rating_history (match_id, participant_id, user_id, sport_id, mmr_before, mmr_after, elo_delta, evaluation_delta, '
      'final_delta, evaluation_score, actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b, algorithm_version, calculated_at)\n')
    rows = []
    for m in matches:
        for h in m['history']:
            rows.append(['(@cx_sm + %d)' % m['no'], '(@cx_p + %d)' % (base[m['no']] + h['pid']), '(@cx_u + %d)' % h['user'],
                         EX_SPORT_VAR[m['sport']], str(h['before']), str(h['after']), str(h['delta']), '0.00', str(h['delta']), 'NULL',
                         str(h['actual'].quantize(Decimal('0.1'))), str(h['expected']), str(m['avg']['A']), str(m['avg']['B']),
                         q(ALGORITHM_VERSION), q(dt(m['rated_at']))])
    w(ex_union(rows, ['match_id', 'participant_id', 'user_id', 'sport_id', 'mmr_before', 'mmr_after', 'elo_delta', 'evaluation_delta',
                      'final_delta', 'evaluation_score', 'actual_score', 'expected_score', 'team_avg_mmr_a', 'team_avg_mmr_b',
                      'algorithm_version', 'calculated_at']))
    w('\nCOMMIT;\n\n')

    user_filter = "u.email LIKE 'club-user-___@spoit.test'"
    names_sql = ', '.join(q(c['name']) for c in EX_CLUBS)
    w('-- 확인 1: 넣은 행 수\n')
    w('SELECT (SELECT COUNT(*) FROM app_user u WHERE %s) AS new_users,\n' % user_filter)
    w('       (SELECT COUNT(*) FROM club_member m JOIN app_user u ON u.user_id = m.user_id WHERE %s AND m.left_at IS NULL) AS new_members,\n' % user_filter)
    w("       (SELECT COUNT(DISTINCT ma.match_id) FROM match_application ma JOIN app_user u ON u.user_id = ma.applicant_user_id\n"
      "          JOIN social_match sm ON sm.match_id = ma.match_id WHERE %s AND ma.status = '확정' AND sm.status = '종료') AS past_matches,\n" % user_filter)
    w('       (SELECT COUNT(*) FROM sport_rating_history h JOIN app_user u ON u.user_id = h.user_id WHERE %s) AS history_rows,\n' % user_filter)
    w("       (SELECT COUNT(*) FROM match_application WHERE receipt_no IN (%s) AND status = '대기' AND group_application_id IS NOT NULL) AS team_applications,\n"
      % ', '.join(q(a['receipt']) for a in waiting))
    w("       (SELECT COUNT(*) FROM club WHERE club_name IN (%s) AND deleted_at IS NULL AND intro LIKE '%%[활동 안내]%%') AS new_intros;\n" % names_sql)
    w('-- 기대값: new_users %d, new_members %d, past_matches %d, history_rows %d, team_applications %d, new_intros %d\n\n'
      % (len(users), len(users), len(matches), history_rows, len(waiting), len(EX_CLUBS)))
    w('-- 확인 2: 새 회원의 레이팅이 이력과 맞는지 (sport_rating = 마지막 이력, 경기 수 = 이력 행 수). 0이면 맞다.\n')
    w('SELECT COUNT(*) AS rating_mismatches\n  FROM app_user u JOIN sport_rating sr ON sr.user_id = u.user_id\n'
      ' WHERE %s\n'
      '   AND (sr.rated_match_count <> (SELECT COUNT(*) FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id)\n'
      '        OR sr.current_mmr <> COALESCE((SELECT h.mmr_after FROM sport_rating_history h WHERE h.user_id = u.user_id AND h.sport_id = sr.sport_id\n'
      '                                        ORDER BY h.calculated_at DESC, h.history_id DESC LIMIT 1), 1000));\n\n' % user_filter)
    w('-- 확인 3: 동호회별 인원과 팀 등급(배치를 마친 동호회원 평균, 기존 회원 포함)\n')
    w('SELECT c.club_name, c.recruit_status, CONCAT(COUNT(m.club_member_id), \'/\', c.max_members) AS members,\n')
    w('       (SELECT ROUND(AVG(sr.current_mmr)) FROM club_member am JOIN sport_rating sr ON sr.user_id = am.user_id AND sr.sport_id = c.sport_id\n')
    w('         WHERE am.club_id = c.club_id AND am.left_at IS NULL AND sr.rated_match_count >= %d) AS average_mmr\n' % PLACEMENT_MATCHES)
    w('  FROM club c JOIN club_member m ON m.club_id = c.club_id AND m.left_at IS NULL\n')
    w(' WHERE c.club_name IN (%s) AND c.deleted_at IS NULL\n' % names_sql)
    w(' GROUP BY c.club_id ORDER BY average_mmr DESC;\n')

    EX_OUT.write_text(''.join(out), encoding='utf-8', newline='\n')
    print('wrote %s (attempt %d)' % (EX_OUT, attempt))
    print('users %d, matches %d, history %d, rooms %d, waiting %d' % (len(users), len(matches), history_rows, len(rooms), len(waiting)))
    for c in play:
        ids = members_of[c['i']]
        played = [m for m in matches if c['i'] in (m['home'], m['away'])]
        wins = sum(1 for m in played if m['result'] == ('A_WIN' if m['home'] == c['i'] else 'B_WIN'))
        placed = [users[u - 1]['mmr'] for u in ids if users[u - 1]['count'] >= PLACEMENT_MATCHES]
        print('  %-10s %-5s games %d wins %d placed %d new_avg %s last %s' % (
            c['name'], c['sport'], len(played), wins, len(placed), sql_avg(placed), max(m['day'] for m in played)))


if __name__ == '__main__':
    main()
