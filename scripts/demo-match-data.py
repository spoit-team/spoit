# -*- coding: utf-8 -*-
"""발표 시연용 매칭방·회원 데이터 SQL 생성기.

대상: 20261110-seoul-public-facilities.sql(공공 체육시설)과 20261109-evaluation-items.sql을 적용한 DB.
출력: 한 트랜잭션 SQL. 데모 회원(demo-001@spoit.test)이 이미 있으면 데모 행은 넣지 않는다.
실행: 저장소 루트에서 python scripts/demo-match-data.py > db/migrations/20261111-demo-match-data.sql (Python 3.8 이상)
같은 시드(RNG)로 만들므로 다시 실행해도 같은 파일이 나온다.
"""
import random
import sys
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

RNG = random.Random(20261123)
PASSWORD_HASH = '$2a$10$07lKk4fyZS6R8PqWjRJ/m.rl7Pe0VJKFj/Za3BiFLAyHkQHAx4OsG'  # spoit1234 (seed 회원과 같은 비밀번호)
NOW_REF = datetime(2026, 11, 11, 12, 0)  # 미래 경기 신청 접수 시각의 상한
FUTURE_DAYS = [date(2026, 11, 12) + timedelta(days=i) for i in range(19)]  # 11/12 ~ 11/30
PAST_DAYS = [date(2026, 11, 1) + timedelta(days=i) for i in range(9)]       # 11/1 ~ 11/9

SPORT_NAME = {'futsal': '축구+풋살', 'tennis': '테니스', 'badminton': '배드민턴', 'basketball': '농구', 'jokgu': '족구'}
SPORT_LABEL = {'futsal': '풋살', 'tennis': '테니스', 'badminton': '배드민턴', 'basketball': '농구', 'jokgu': '족구'}
ITEM_CODES = {
    'futsal': ['SOCCER_TECH', 'FUTSAL_PASS', 'FUTSAL_POSITION'],
    'tennis': ['TENNIS_SERVE', 'TENNIS_RALLY', 'TENNIS_POSITION'],
    'badminton': ['BADMINTON_SMASH', 'BADMINTON_FOOTWORK', 'BADMINTON_SERVE'],
    'basketball': ['BASKET_SHOOT', 'BASKET_PASS', 'BASKET_DEFENSE'],
    'jokgu': ['JOKGU_KICK', 'JOKGU_TOSS', 'JOKGU_DEFENSE'],
}
# 종목별 기본 사진. src/main/resources/static/images/matches/의 정적 파일이라 로컬·운영(S3 저장소 포함)에서 같은 경로로 열린다.
# /uploads/로 시작하지 않으므로 매칭방 사진을 바꿀 때 업로드 저장소가 이 공용 파일을 지우지 않는다.
PHOTO_FILE = {'futsal': 'soccer', 'tennis': 'tennis', 'badminton': 'badminton', 'basketball': 'basketball', 'jokgu': 'jokgu'}
PHOTO = {s: '/images/matches/%s.jpg' % f for s, f in PHOTO_FILE.items()}
WEEKDAY_KO = ['월','화', '수', '목', '금', '토', '일']

# 공공 체육시설 (구, 시설명, 종목, 여는 시각, 닫는 시각). 20261110-seoul-public-facilities.sql의 시설 중 장애인 전용 구장을 뺀 것.
# 매칭방은 구별 사용 횟수가 가장 적은 구의 시설부터 골라 21개 구에 고르게 퍼뜨린다.
FACILITY_DATA = [
    ('강남구', '동부도로사업소 테니스장', ('tennis',), 6, 22),
    ('강남구', '마루공원', ('tennis', 'futsal', 'jokgu'), 6, 22),
    ('강남구', '서울물재생시설공단 탄천물재생센터', ('tennis',), 6, 22),
    ('강남구', '에코파크', ('futsal', 'jokgu'), 6, 22),
    ('강남구', '탄천센터', ('futsal',), 6, 19),
    ('강동구', '광나루 한강공원 테니스장', ('tennis',), 8, 22),
    ('강동구', '광나루한강공원 농구장 1, 2', ('basketball',), 6, 20),
    ('강동구', '광나루한강공원 족구장1(안내센터상류)', ('jokgu',), 6, 20),
    ('강동구', '광나루한강공원 족구장2(안내센터상류)', ('jokgu',), 6, 20),
    ('강동구', '광나루한강공원 족구장3(안내센터상류)', ('jokgu',), 6, 20),
    ('강동구', '광나루한강공원 축구장 2', ('futsal',), 6, 20),
    ('강동구', '광나루한강공원 축구장 3', ('futsal',), 6, 20),
    ('강서구', '강서한강공원 족구장(1,2)', ('jokgu',), 6, 20),
    ('강서구', '서남물재생센터', ('tennis', 'futsal'), 6, 22),
    ('관악구', '난우공원 테니스장', ('tennis',), 10, 20),
    ('관악구', '선우공원', ('tennis',), 6, 22),
    ('광진구', '뚝섬 한강공원 테니스장', ('tennis',), 8, 22),
    ('광진구', '뚝섬한강공원 농구장 1', ('basketball',), 6, 20),
    ('광진구', '뚝섬한강공원 농구장 2', ('basketball',), 6, 20),
    ('광진구', '뚝섬한강공원 배드민턴장 1', ('badminton',), 6, 20),
    ('광진구', '뚝섬한강공원 배드민턴장 2', ('badminton',), 6, 20),
    ('광진구', '뚝섬한강공원 배드민턴장 3', ('badminton',), 6, 20),
    ('광진구', '뚝섬한강공원 족구장 1', ('jokgu',), 6, 20),
    ('광진구', '뚝섬한강공원 족구장 2', ('jokgu',), 6, 20),
    ('광진구', '뚝섬한강공원 축구장 1', ('futsal',), 6, 22),
    ('광진구', '뚝섬한강공원 축구장 2', ('futsal',), 6, 22),
    ('광진구', '서울어린이대공원 잔디축구장', ('futsal',), 6, 22),
    ('광진구', '어린이대공원 테니스장 A코트', ('tennis',), 6, 22),
    ('광진구', '어린이대공원 테니스장 B코트', ('tennis',), 6, 22),
    ('광진구', '어린이대공원 풋살장 3:3', ('futsal',), 6, 22),
    ('광진구', '어린이대공원 풋살장 5:5', ('futsal',), 6, 22),
    ('구로구', '계남근린공원', ('tennis',), 6, 22),
    ('구로구', '계남근린공원 인조잔디축구장', ('futsal',), 8, 18),
    ('구로구', '고척스카이돔', ('futsal',), 6, 22),
    ('구로구', '신도림테니스장', ('tennis',), 7, 21),
    ('구로구', '안양천 체육시설 풋살경기장(구일역 하부)', ('futsal',), 8, 24),
    ('금천구', '독산테니스장', ('tennis',), 6, 22),
    ('도봉구', '다락원체육공원', ('tennis',), 6, 22),
    ('동대문구', '한국외대 인조잔디 구장', ('futsal',), 6, 22),
    ('동작구', '노들나루공원(노들배수지공원)', ('futsal', 'jokgu'), 6, 22),
    ('동작구', '노량진 축구장', ('futsal',), 6, 20),
    ('동작구', '동작구 테니스장', ('tennis',), 6, 22),
    ('동작구', '보라매공원', ('tennis',), 6, 22),
    ('동작구', '보라매공원 인조잔디축구장', ('futsal',), 6, 22),
    ('동작구', '현충실내배드민턴장', ('badminton',), 9, 17),
    ('마포구', '난지 풋살장', ('futsal',), 6, 20),
    ('마포구', '난지농구장', ('basketball',), 6, 20),
    ('마포구', '난지천 공원 풋살장', ('futsal',), 7, 18),
    ('마포구', '난지천 인조잔디 축구장', ('futsal',), 6, 22),
    ('마포구', '난지한강공원 족구장', ('jokgu',), 6, 20),
    ('마포구', '난지한강테니스장', ('tennis',), 8, 22),
    ('마포구', '망원나들목 테니스장', ('tennis',), 9, 19),
    ('마포구', '망원유수지 소프트테니스장', ('tennis',), 8, 22),
    ('마포구', '망원유수지 풋살장', ('futsal',), 6, 22),
    ('마포구', '망원족구', ('jokgu',), 6, 20),
    ('마포구', '망원한강공원 농구장(1,2)', ('basketball',), 6, 20),
    ('마포구', '망원한강공원 축구장', ('futsal',), 6, 20),
    ('마포구', '망원한강테니스장', ('tennis',), 8, 22),
    ('마포구', '서울월드컵경기장 보조구장', ('futsal',), 6, 22),
    ('마포구', '서울월드컵경기장 풋살구장', ('futsal',), 6, 22),
    ('마포구', '월드컵공원 테니스장', ('tennis',), 7, 21),
    ('서대문구', '가좌테니스장', ('tennis',), 6, 22),
    ('서대문구', '현저테니스장', ('tennis',), 11, 22),
    ('서대문구', '홍은테니스장', ('tennis',), 11, 21),
    ('서초구', '반포 농구장(안내센터 앞)', ('basketball',), 6, 20),
    ('서초구', '반포한강공원 농구장(1,2)', ('basketball',), 6, 20),
    ('서초구', '반포한강공원 축구장', ('futsal',), 6, 20),
    ('서초구', '방배배수지체육공원', ('futsal',), 6, 22),
    ('서초구', '양재근린공원 축구장', ('futsal',), 6, 22),
    ('서초구', '인재개발원 축구장', ('futsal',), 7, 19),
    ('서초구', '인재개발원 테니스장', ('tennis',), 13, 19),
    ('서초구', '잠원한강공원 농구장(1,2,3,4)', ('basketball',), 6, 20),
    ('서초구', '잠원한강공원 축구장', ('futsal',), 6, 20),
    ('서초구', '잠원한강테니스장', ('tennis',), 8, 22),
    ('성동구', '서울숲 배드민턴장2', ('badminton',), 9, 17),
    ('성동구', '서울숲 테니스장', ('tennis',), 6, 19),
    ('성동구', '응봉공원', ('tennis',), 7, 19),
    ('성동구', '이촌농구장(금호나들목)', ('basketball',), 6, 20),
    ('성동구', '중랑물재생센터 배드민턴장', ('badminton',), 7, 21),
    ('성동구', '중랑물재생센터 족구장', ('jokgu',), 9, 17),
    ('성동구', '중랑물재생센터 축구장', ('futsal',), 9, 17),
    ('성동구', '중랑물재생센터 테니스장', ('tennis',), 9, 17),
    ('성동구', '중랑물재생센터 풋살장', ('futsal',), 7, 21),
    ('성북구', '월곡테니스장', ('tennis',), 6, 22),
    ('성북구', '정릉테니스장', ('tennis',), 13, 20),
    ('송파구', '광나루 농구장(올림픽대로 방향)', ('basketball',), 6, 20),
    ('송파구', '잠실 농구장', ('basketball',), 6, 20),
    ('송파구', '잠실종합운동장 제2풋살경기장', ('futsal',), 7, 23),
    ('송파구', '잠실종합운동장 풋살경기장', ('futsal',), 7, 23),
    ('송파구', '잠실한강공원 족구장', ('jokgu',), 6, 20),
    ('양천구', '서서울호수공원', ('futsal',), 6, 22),
    ('양천구', '서울에너지공사 목동 테니스장 1면', ('tennis',), 8, 19),
    ('양천구', '서울에너지공사 목동 테니스장 2면', ('tennis',), 8, 19),
    ('영등포구', '남부도로사업소 테니스장', ('tennis',), 6, 22),
    ('영등포구', '양화한강공원 축구장', ('futsal',), 6, 20),
    ('영등포구', '영등포공원 풋살경기장', ('futsal',), 8, 20),
    ('용산구', '이촌 풋살장(한남역방향)', ('futsal',), 6, 20),
    ('용산구', '이촌 한강공원 테니스장', ('tennis',), 8, 22),
    ('용산구', '이촌농구(청소년광장)', ('basketball',), 6, 20),
    ('용산구', '이촌농구장(인라인장옆)', ('basketball',), 6, 20),
    ('용산구', '이촌족구장1(한남)', ('jokgu',), 6, 20),
    ('용산구', '이촌족구장2,3(보광나들목1,2)', ('jokgu',), 6, 20),
    ('용산구', '이촌족구장4,5(한강대교 북단 상류 1,2)', ('jokgu',), 6, 20),
    ('용산구', '이촌족구장6(한강대교 북단 하류)', ('jokgu',), 6, 20),
    ('용산구', '이촌족구장7,8(원효대교 북단 상류 1,2)', ('jokgu',), 6, 20),
    ('용산구', '이촌한강공원 축구장', ('futsal',), 6, 20),
    ('용산구', '이촌한강공원 풋살경기장(옥수역하부)', ('futsal',), 6, 20),
    ('용산구', '한남테니스장', ('tennis',), 6, 22),
    ('용산구', '효창운동장(축구장,육상트랙)', ('futsal',), 8, 22),
    ('종로구', '삼청테니스장', ('tennis',), 6, 21),
    ('중구', '장충테니스장', ('tennis',), 6, 22),
]
DISTRICTS = sorted({f[0] for f in FACILITY_DATA})
gu_use = {g: 0 for g in DISTRICTS}
fac_use = {}
gu_sport_use = {}
fac_busy = {}


def choose_facility(sport, s, e):
    cands = [f for f in FACILITY_DATA
             if sport in f[2] and f[3] <= s.hour and (e.hour or 24) <= f[4]
             and all(e <= bs or s >= be for bs, be in fac_busy.get(f[:2], []))]
    if not cands:
        raise SystemExit('시설 부족: %s %s' % (sport, s))
    # 구 전체 사용 횟수와 그 구에서 같은 종목을 쓴 횟수를 함께 봐서, 구마다 방 수와 종목이 고르게 섞이게 한다.
    fac = min(cands, key=lambda f: (gu_use[f[0]] + 2 * gu_sport_use.get((f[0], sport), 0),
                                    fac_use.get(f[:2], 0), RNG.random()))
    gu_use[fac[0]] += 1
    gu_sport_use[(fac[0], sport)] = gu_sport_use.get((fac[0], sport), 0) + 1
    fac_use[fac[:2]] = fac_use.get(fac[:2], 0) + 1
    fac_busy.setdefault(fac[:2], []).append((s, e))
    return fac[:2]


SURNAMES = list('김이박최정강조윤장임한오서신권황안송류홍')
GIVEN = ['민준', '서연', '도윤', '하은', '시우', '지우', '주원', '서윤', '하준', '지민', '예준', '수아', '건우', '지유', '현우', '채원',
         '우진', '다은', '선우', '예린', '연우', '소율', '유준', '하린', '정우', '윤서', '승현', '가은', '준혁', '나연', '지호', '은채',
         '태윤', '서현', '민재', '유나', '은우', '수빈', '재원', '지아']


def lit(v):
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'TRUE' if v else 'FALSE'
    if isinstance(v, (int, Decimal)):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, datetime):
        return "'%s'" % v.strftime('%Y-%m-%d %H:%M:%S')
    if isinstance(v, Raw):
        return v.s
    return "'%s'" % str(v).replace("'", "''")


class Raw:
    def __init__(self, s):
        self.s = s


def ref(var, n):
    return Raw('(%s + %d)' % (var, n))


# ---------------------------------------------------------------- 회원
class User:
    def __init__(self, n, email, nickname, sports, district):
        self.n, self.email, self.nickname, self.sports, self.district = n, email, nickname, sports, district
        self.busy = []
        self.mmr = {s: Decimal(int(min(1350, max(760, RNG.gauss(1000, 120))))) for s in sports}
        self.count = {s: RNG.randint(3, 18) for s in sports}


users = []
names = set()
sport_keys = list(SPORT_NAME)
weights = {'futsal': 0.32, 'basketball': 0.22, 'tennis': 0.2, 'badminton': 0.16, 'jokgu': 0.1}
for i in range(1, 101):
    while True:
        nick = RNG.choice(SURNAMES) + RNG.choice(GIVEN)
        if nick not in names:
            names.add(nick)
            break
    primary = RNG.choices(sport_keys, [weights[s] for s in sport_keys])[0]
    extra = [s for s in sport_keys if s != primary and RNG.random() < 0.35]
    users.append(User(i, 'demo-%03d@spoit.test' % i, nick, [primary] + extra[:2], RNG.choice(DISTRICTS)))
presenter = User(101, 'demo-presenter@spoit.test', '발표자', [], '광진구')
presenter.mmr, presenter.count = {}, {}


def free(u, s, e):
    return all(e <= bs or s >= be for bs, be in u.busy)


def pick(sport, k, s, e, pool=None, exclude=()):
    cands = [u for u in (pool or users) if sport in u.sports and u not in exclude and free(u, s, e)]
    if len(cands) < k:
        raise SystemExit('회원 부족: %s %d명 필요, %d명 가능 (%s)' % (sport, k, len(cands), s))
    chosen = RNG.sample(cands, k)
    for u in chosen:
        u.busy.append((s, e))
    return chosen


# ---------------------------------------------------------------- 동호회
CLUB_DEFS = [
    ('뚝섬 FC', 'futsal', ['광진구'], '6,7', '10:00', '12:00', 9),
    ('망원 풋살 크루', 'futsal', ['마포구'], '2,4', '19:00', '21:00', 9),
    ('잠실 풋살 클럽', 'futsal', ['송파구', '강동구'], '6', '14:00', '16:00', 8),
    ('반포 농구단', 'basketball', ['서초구'], '3,6', '18:00', '20:00', 8),
    ('광나루 바스켓', 'basketball', ['강동구', '송파구'], '7', '14:00', '16:00', 8),
    ('한강 족구회', 'jokgu', ['광진구', '강동구'], '6,7', '10:00', '12:00', 7),
    ('뚝섬 셔틀콕', 'badminton', ['광진구', '성동구'], '2,5', '18:00', '20:00', 7),
    ('마포 랠리 테니스', 'tennis', ['마포구'], '6', '10:00', '12:00', 7),
]
clubs = []
for ci, (name, sport, gus, days, st, et, size) in enumerate(CLUB_DEFS):
    taken = {u for c in clubs if c['sport'] == sport for u in c['members']}
    pool = [u for u in users if sport in u.sports and u not in taken]
    members = RNG.sample(pool, size)
    clubs.append({'n': ci, 'name': name, 'sport': sport, 'gus': gus, 'days': days, 'st': st, 'et': et, 'members': members})


def club_of(sport, idx):
    cs = [c for c in clubs if c['sport'] == sport]
    return cs[idx % len(cs)]


def pick_team(club, k, s, e):
    """대표(동호회장)를 포함해 시간이 비는 동호회원 k명. 대표가 바쁘면 None."""
    leader = club['members'][0]
    if not free(leader, s, e):
        return None
    rest = [u for u in club['members'][1:] if free(u, s, e)]
    if len(rest) < k - 1:
        return None
    chosen = [leader] + RNG.sample(rest, k - 1)
    for u in chosen:
        u.busy.append((s, e))
    return chosen


# ---------------------------------------------------------------- 매칭방
rooms = []          # dict
applications = []   # dict
participants = []   # dict
receipts = set()
ALPHA = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'


def receipt(at):
    while True:
        r = 'RCPT-%s-%s' % (at.strftime('%Y%m%d'), ''.join(RNG.choice(ALPHA) for _ in range(8)))
        if r not in receipts:
            receipts.add(r)
            return r


def slot_name(h):
    return '오전' if h < 12 else ('오후' if h < 17 else '저녁')


def add_room(day, sport, role, idx, sh, eh, cap, fee, skill, kind='future'):
    s = datetime(day.year, day.month, day.day, sh)
    e = s + timedelta(hours=eh - sh)
    fac = choose_facility(sport, s, e)
    title = '%s %s요일 %s %s %d:%d' % (fac[0] if len(fac[0]) == 2 else fac[0][:-1], WEEKDAY_KO[day.weekday()], slot_name(sh), SPORT_LABEL[sport], cap, cap)
    room = {'n': len(rooms), 'fac': fac, 'sport': sport, 'title': title, 's': s, 'e': e, 'cap': cap,
            'fee': Decimal(fee), 'skill': skill, 'kind': kind, 'apps': []}
    rooms.append(room)
    return room


def applied_at(room, past):
    if past:
        return room['s'] - timedelta(days=RNG.randint(2, 5), hours=RNG.randint(0, 10))
    lo, hi = datetime(2026, 11, 9, 9), NOW_REF
    return lo + timedelta(seconds=RNG.randint(0, int((hi - lo).total_seconds())))


def add_individual(room, u, status, past=False):
    at = applied_at(room, past)
    app = {'n': len(applications), 'room': room, 'user': u, 'club': None, 'type': '개인', 'status': status,
           'receipt': receipt(at), 'created': at, 'confirmed': at + timedelta(hours=RNG.randint(1, 6)) if status == '확정' else None,
           'group': None}
    if app['confirmed'] and not past and app['confirmed'] > NOW_REF:
        app['confirmed'] = NOW_REF
    applications.append(app)
    room['apps'].append(app)
    if status == '확정':
        participants.append({'n': len(participants), 'app': app, 'room': room, 'user': u,
                             'mmr': u.mmr.get(room['sport'], Decimal(1000)), 'joined': app['confirmed'], 'side': None})
    return app


def add_team(room, club, members, status, past=False):
    at = applied_at(room, past)
    leader_app = None
    for u in members:
        app = {'n': len(applications), 'room': room, 'user': u, 'club': club, 'type': '동호회', 'status': status,
               'receipt': receipt(at), 'created': at,
               'confirmed': min(at + timedelta(hours=3), NOW_REF) if status == '확정' else None, 'group': None}
        leader_app = leader_app or app
        app['group'] = leader_app
        applications.append(app)
        room['apps'].append(app)
        if status == '확정':
            participants.append({'n': len(participants), 'app': app, 'room': room, 'user': u,
                                 'mmr': u.mmr.get(room['sport'], Decimal(1000)), 'joined': app['confirmed'], 'side': None})


def fill(room, confirmed=0, waiting=0, team=None, past=False):
    s, e = room['s'], room['e']
    if team:
        club, k, status = team
        members = pick_team(club, k, s, e)
        if members:
            add_team(room, club, members, status, past)
            if status == '확정':
                confirmed -= k
    for u in pick(room['sport'], confirmed, s, e):
        add_individual(room, u, '확정', past)
    for u in pick(room['sport'], waiting, s, e):
        add_individual(room, u, '대기', past)


SKILLS = ['초급', '중급', '중급', '고급']
for di, day in enumerate(FUTURE_DAYS):
    weekend = day.weekday() >= 5
    sk = lambda: RNG.choice(SKILLS)
    if not weekend:
        fill(add_room(day, 'futsal', 'A', di, 19, 21, 5, 0, '중급'), confirmed=10)
        add_room(day, 'futsal', 'B', di, 20, 22, 6, 8000, sk())
        fill(add_room(day, 'tennis', 'A', di, 18, 20, 2, 0, sk()), confirmed=4)
        add_room(day, 'tennis', 'B', di, 19, 21, 2, 4000, '초급')
        fill(add_room(day, 'badminton', 'A', di, 18, 20, 2, 0, '초급'), confirmed=2, waiting=1)
        add_room(day, 'badminton', 'B', di, 18, 20, 2, 5000, sk())
        fill(add_room(day, 'basketball', 'A', di, 18, 20, 3, 0, sk()), confirmed=6)
        fill(add_room(day, 'basketball', 'B', di, 18, 20, 4, 0, '초급'), confirmed=5, waiting=2)
        fill(add_room(day, 'jokgu', 'A', di, 18, 20, 3, 0, '초급'), confirmed=3)
    else:
        fill(add_room(day, 'futsal', 'A', di, 10, 12, 5, 0, '중급'), confirmed=10, team=(club_of('futsal', di), 5, '확정'))
        fill(add_room(day, 'futsal', 'B', di, 14, 16, 6, 0, sk()), confirmed=4, team=(club_of('futsal', di + 1), 3, '대기'))
        fill(add_room(day, 'tennis', 'A', di, 13, 15, 2, 0, sk()), confirmed=2, waiting=1)
        add_room(day, 'tennis', 'B', di, 10, 12, 2, 4000, sk())
        fill(add_room(day, 'badminton', 'WA', di, 10, 12, 2, 0, '초급'), confirmed=4)
        add_room(day, 'badminton', 'WB', di, 14, 16, 2, 5000, sk())
        fill(add_room(day, 'basketball', 'B', di, 14, 16, 4, 0, sk()), confirmed=2, team=(club_of('basketball', di), 4, '대기'))
        add_room(day, 'basketball', 'A', di, 10, 12, 3, 6000, sk())
        fill(add_room(day, 'jokgu', 'A', di, 10, 12, 3, 0, '초급'), confirmed=6)

# 지난 경기: 결과·평가까지 넣고 레이팅은 앱 스케줄러가 마감 처리로 반영한다.
PAST_PLAN = ['futsal', 'tennis', 'badminton', 'basketball', 'jokgu']
PAST_CAP = {'futsal': 5, 'tennis': 2, 'badminton': 2, 'basketball': 3, 'jokgu': 3}
k = 0
for di, day in enumerate(PAST_DAYS):
    weekend = day.weekday() >= 5
    for slot in range(2):
        sport = PAST_PLAN[k % len(PAST_PLAN)]
        k += 1
        role = 'A' if slot == 0 else 'B'
        idx = di + (0 if role != 'A' or slot == 0 else 2)
        if sport == 'badminton':
            role = 'WA' if weekend else ('A' if slot == 0 else 'B')
        sh = (10 if slot == 0 else 14) if weekend else 18
        room = add_room(day, sport, role, idx, sh, sh + 2, PAST_CAP[sport], 0, RNG.choice(SKILLS), kind='past')
        fill(room, confirmed=PAST_CAP[sport] * 2, past=True)

# 지난 경기 팀 배정·결과·평가
submissions, evaluations = [], []
for room in rooms:
    if room['kind'] != 'past':
        continue
    ps = sorted([p for p in participants if p['room'] is room], key=lambda p: -p['mmr'])
    for i, p in enumerate(ps):  # 스네이크 배정
        p['side'] = 'A' if (i % 4) in (0, 3) else 'B'
    team_a = [p for p in ps if p['side'] == 'A']
    team_b = [p for p in ps if p['side'] == 'B']
    room['cap_a'], room['cap_b'] = team_a[0], team_b[0]
    room['result'] = RNG.choices(['A_WIN', 'B_WIN', 'DRAW'], [0.45, 0.4, 0.15])[0]
    for j, cap in enumerate((team_a[0], team_b[0])):
        submissions.append({'room': room, 'cap': cap, 'at': room['e'] + timedelta(minutes=40 + 30 * j)})
        for t in ps:
            if t is cap:
                continue
            base = 4 + float(t['mmr'] - 760) / 590 * 4
            scores = [max(0, min(10, int(round(RNG.gauss(base, 1.2))))) for _ in range(3)]
            overall = (Decimal(sum(scores)) / Decimal(3)).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
            evaluations.append({'n': len(evaluations), 'room': room, 'eval': cap, 'target': t, 'scores': scores,
                                'overall': overall, 'at': room['e'] + timedelta(hours=1, minutes=10 + 20 * j)})


# ---------------------------------------------------------------- SQL 출력
out = []
w = out.append


def insert(table, cols, rows, chunk=150):
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        sel = []
        for r_i, row in enumerate(part):
            vals = [lit(v) for v in row]
            if r_i == 0:
                vals = ['%s AS %s' % (v, c) for v, c in zip(vals, cols)]
            sel.append(('                 SELECT ' if r_i == 0 else '       UNION ALL SELECT ') + ', '.join(vals))
        w('INSERT INTO %s (%s)\nSELECT * FROM (\n%s\n) v\n WHERE @demo_ready = 1;\n' % (table, ', '.join(cols), '\n'.join(sel)))


# SQL에서 이름으로 찾을 시설: 매칭방에 쓰인 시설만, 처음 쓰인 순서대로 변수 번호를 매긴다.
FACILITIES = []
for r in rooms:
    if r['fac'] not in FACILITIES:
        FACILITIES.append(r['fac'])
FAC_VAR = {f: '@df%d' % (i + 1) for i, f in enumerate(FACILITIES)}

all_users = users + [presenter]
w("""-- =====================================================================
-- 발표 시연 데이터: 공공 체육시설 매칭방 %d개, 데모 회원 %d명, 동호회 %d개
-- 이 파일은 scripts/demo-match-data.py가 만든다. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.
-- 대상: 20261110-seoul-public-facilities.sql과 20261109-evaluation-items.sql을 적용한 DB (로컬·운영)
-- 내용
--   * 데모 회원 demo-001~100@spoit.test, 발표자 demo-presenter@spoit.test (비밀번호 모두 spoit1234)
--   * 11/12~11/30 매일 매칭방 9개 (정원이 찬 방은 시작 시각에 자동 배정되어 경기 후 결과·평가 입력 가능)
--   * 11/1~11/9 지난 경기 %d개: 결과·평가까지 넣고 rating_status WAITING으로 둔다.
--     앱 스케줄러가 결과 마감(종료 24시간 후) 처리로 레이팅을 반영하고 종료로 바꾼다.
--   * 매칭방 사진: 사진이 없는 모든 매칭방과 seed의 /uploads/matches/seed/ 사진(git에 없어 운영에서 열리지 않음)을
--     종목별 정적 사진 /images/matches/종목.jpg로 채운다. 직접 올린 사진은 바꾸지 않는다.
-- 원칙
--   * 한 트랜잭션. 사전 확인에 실패하면 멈추고 아무 행도 넣지 않는다.
--   * demo-001@spoit.test가 이미 있으면 이미 적용된 것으로 보고 데모 행은 넣지 않는다(오류 없음). 사진 채우기는 다시 실행해도 결과가 같다.
--   * 시설·종목·평가 항목·담당자는 이름으로 찾는다(DB마다 번호가 다르다). 새 행 번호는 각 테이블의 현재 최대 번호 다음부터 쓴다.
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;
""" % (len(rooms), len(all_users), len(clubs), sum(1 for r in rooms if r['kind'] == 'past')))

w('-- 0. 이름으로 번호 찾기')
for sk_, sn in SPORT_NAME.items():
    w("SET @ds_%s = (SELECT MAX(sport_id) FROM sport WHERE name = %s);" % (sk_, lit(sn)))
for f in FACILITIES:
    v = FAC_VAR[f]
    w("SET %s = (SELECT MAX(f.facility_id) FROM facility f JOIN region r ON r.region_id = f.region_id AND r.parent_region_id = 1 "
      "WHERE r.name = %s AND f.name = %s AND f.deleted_at IS NULL);" % (v, lit(f[0]), lit(f[1])))
    w("SET %s_mgr = (SELECT MIN(fm.user_id) FROM facility_manager fm JOIN app_user u ON u.user_id = fm.user_id "
      "WHERE fm.facility_id = %s AND u.email LIKE 'facility-__@spoit.test');" % (v, v))
for d in DISTRICTS:
    w("SET @dr_%s = (SELECT MAX(region_id) FROM region WHERE parent_region_id = 1 AND name = %s);" % (d, lit(d)))
for sk_, codes in ITEM_CODES.items():
    for i, c in enumerate(codes):
        w("SET @di_%s_%d = (SELECT MAX(item_id) FROM sport_evaluation_item WHERE item_code = %s AND is_active = TRUE AND sport_id = @ds_%s);"
          % (sk_, i, lit(c), sk_))

fac_checks = ' + '.join('(%s IS NOT NULL AND %s_mgr IS NOT NULL)' % (FAC_VAR[f], FAC_VAR[f]) for f in FACILITIES)
item_checks = ' + '.join('(@di_%s_%d IS NOT NULL)' % (s, i) for s in ITEM_CODES for i in range(3))
sport_checks = ' + '.join('(@ds_%s IS NOT NULL)' % s for s in SPORT_NAME)
region_checks = ' + '.join('(@dr_%s IS NOT NULL)' % d for d in DISTRICTS)
club_names = ', '.join(lit(c['name']) for c in clubs)
w("""
-- 사전 확인. 출력되는 값이 모두 1이어야 진행한다. already_applied가 1이면 이미 적용된 DB라 아무것도 넣지 않는다.
SELECT (%s) = 5, (%s) = %d, (%s) = %d, (%s) = 15,
       NOT EXISTS (SELECT 1 FROM club WHERE active_club_name IN (%s)),
       EXISTS (SELECT 1 FROM app_user WHERE email = 'demo-001@spoit.test')
  INTO @demo_sports, @demo_facilities, @demo_regions, @demo_items, @demo_club_names_free, @demo_already_applied;
SET @demo_checks = @demo_sports AND @demo_facilities AND @demo_regions AND @demo_items AND @demo_club_names_free;
SET @demo_ready = @demo_checks AND NOT @demo_already_applied;
SELECT @demo_sports AS sports, @demo_facilities AS facilities, @demo_regions AS districts, @demo_items AS evaluation_items,
       @demo_club_names_free AS club_names_free, @demo_already_applied AS already_applied;
-- 이미 적용된 DB가 아닌데 확인에 실패하면 "Subquery returns more than 1 row" 오류로 멈춘다.
DO (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE @demo_checks = 0 AND @demo_already_applied = 0);

SELECT MAX(user_id), (SELECT COALESCE(MAX(club_id), 0) FROM club), (SELECT COALESCE(MAX(club_member_id), 0) FROM club_member),
       (SELECT COALESCE(MAX(match_id), 0) FROM social_match), (SELECT COALESCE(MAX(application_id), 0) FROM match_application),
       (SELECT COALESCE(MAX(participant_id), 0) FROM match_participant), (SELECT COALESCE(MAX(evaluation_id), 0) FROM match_evaluation)
  INTO @du, @dc, @dcm, @dm, @da, @dp, @de
  FROM app_user;
""" % (sport_checks, fac_checks, len(FACILITIES), region_checks, len(DISTRICTS), item_checks, club_names))

w('-- 1. 회원')
insert('app_user', ['user_id', 'email', 'password_hash', 'nickname', 'phone', 'role', 'created_at'],
       [[ref('@du', u.n), u.email, PASSWORD_HASH, u.nickname, '010-7%03d-%04d' % (u.n, RNG.randint(0, 9999)), 'MEMBER',
         datetime(2026, 10, 1) + timedelta(days=RNG.randint(0, 25), hours=RNG.randint(8, 22))] for u in all_users])
insert('user_region', ['user_id', 'region_id'], [[ref('@du', u.n), Raw('@dr_' + u.district)] for u in all_users])
insert('user_preferred_sport', ['user_id', 'sport_id'],
       [[ref('@du', u.n), Raw('@ds_' + (u.sports[0] if u.sports else 'futsal'))] for u in all_users])
insert('sport_rating', ['user_id', 'sport_id', 'current_mmr', 'rated_match_count', 'updated_at'],
       [[ref('@du', u.n), Raw('@ds_' + s), u.mmr[s], u.count[s], datetime(2026, 10, 31, 22)] for u in users for s in u.sports])

w('-- 2. 동호회')
insert('club', ['club_id', 'sport_id', 'club_name', 'intro', 'max_members', 'recruit_status', 'activity_days',
                'activity_start_time', 'activity_end_time', 'created_at'],
       [[ref('@dc', c['n'] + 1), Raw('@ds_' + c['sport']), c['name'],
         '%s에서 함께 %s 하는 동호회입니다. 초보도 환영합니다.' % (', '.join(c['gus']), SPORT_LABEL[c['sport']]),
         20, '모집중', c['days'], c['st'] + ':00', c['et'] + ':00', datetime(2026, 9, 1) + timedelta(days=c['n'] * 3)] for c in clubs])
insert('club_region', ['club_id', 'region_id'], [[ref('@dc', c['n'] + 1), Raw('@dr_' + g)] for c in clubs for g in c['gus']])
cm_rows, cm_n = [], 0
for c in clubs:
    for i, u in enumerate(c['members']):
        cm_n += 1
        cm_rows.append([ref('@dcm', cm_n), ref('@dc', c['n'] + 1), ref('@du', u.n), '동호회장' if i == 0 else '동호회원',
                        datetime(2026, 9, 1) + timedelta(days=c['n'] * 3 + i)])
insert('club_member', ['club_member_id', 'club_id', 'user_id', 'role', 'joined_at'], cm_rows)

w('-- 3. 매칭방 (지난 경기는 팀 배정을 끝낸 매칭 완료 상태로 넣고, 팀장은 참가자를 넣은 뒤 지정한다)')
room_rows = []
for r in rooms:
    fv = FAC_VAR[r['fac']]
    past = r['kind'] == 'past'
    room_rows.append([ref('@dm', r['n'] + 1), Raw(fv), Raw('@ds_' + r['sport']), Raw(fv + '_mgr'), r['title'], r['s'], r['e'],
                      r['cap'] * 2, r['cap'] * 2, r['cap'], r['cap'], r['fee'], r['skill'],
                      '매칭 완료' if past else '모집 중', 'AUTO' if past else 'MANUAL',
                      Raw(fv + '_mgr') if past else None, (r['s'] - timedelta(hours=6)) if past else None,
                      'CONFIRMED' if past else None, r.get('result'), (r['e'] + timedelta(minutes=70)) if past else None,
                      'WAITING', PHOTO[r['sport']]])
insert('social_match', ['match_id', 'facility_id', 'sport_id', 'created_by', 'title', 'start_at', 'end_at', 'min_participants',
                        'max_participants', 'team_a_capacity', 'team_b_capacity', 'fee_per_person', 'required_skill', 'status',
                        'assignment_method', 'assigned_by', 'assignment_confirmed_at', 'result_status', 'final_result',
                        'result_confirmed_at', 'rating_status', 'photo_url'], room_rows)

w('-- 4. 신청 (팀 신청은 팀원마다 한 행, 묶음 번호는 아래에서 대표 행 번호로 채운다)')
insert('match_application', ['application_id', 'match_id', 'applicant_user_id', 'club_id', 'application_type', 'status',
                             'receipt_no', 'created_at', 'confirmed_at'],
       [[ref('@da', a['n'] + 1), ref('@dm', a['room']['n'] + 1), ref('@du', a['user'].n),
         ref('@dc', a['club']['n'] + 1) if a['club'] else None, a['type'], a['status'], a['receipt'], a['created'], a['confirmed']]
        for a in applications])
groups = {}
for a in applications:
    if a['group']:
        groups.setdefault(a['group']['n'], []).append(a['n'])
for lead, members in groups.items():
    w('UPDATE match_application SET group_application_id = (@da + %d) WHERE @demo_ready = 1 AND application_id IN (%s);'
      % (lead + 1, ', '.join('(@da + %d)' % (m + 1) for m in members)))

w('\n-- 5. 확정 참가자')
insert('match_participant', ['participant_id', 'application_id', 'match_id', 'user_id', 'participation_status', 'match_side',
                             'entry_mmr', 'joined_at'],
       [[ref('@dp', p['n'] + 1), ref('@da', p['app']['n'] + 1), ref('@dm', p['room']['n'] + 1), ref('@du', p['user'].n), '확정',
         p['side'], p['mmr'], p['joined']] for p in participants])
for r in rooms:
    if r['kind'] == 'past':
        w('UPDATE social_match SET captain_a_participant_id = (@dp + %d), captain_b_participant_id = (@dp + %d) '
          'WHERE @demo_ready = 1 AND match_id = (@dm + %d);' % (r['cap_a']['n'] + 1, r['cap_b']['n'] + 1, r['n'] + 1))

w('\n-- 6. 지난 경기 결과 제출·평가')
insert('match_result_submission', ['match_id', 'captain_participant_id', 'reported_result', 'submission_status', 'submitted_at'],
       [[ref('@dm', s['room']['n'] + 1), ref('@dp', s['cap']['n'] + 1), s['room']['result'], 'CONFIRMED', s['at']] for s in submissions])
insert('match_evaluation', ['evaluation_id', 'match_id', 'sport_id', 'evaluator_participant_id', 'target_participant_id',
                            'overall_score', 'scale_max', 'status', 'submitted_at'],
       [[ref('@de', ev['n'] + 1), ref('@dm', ev['room']['n'] + 1), Raw('@ds_' + ev['room']['sport']), ref('@dp', ev['eval']['n'] + 1),
         ref('@dp', ev['target']['n'] + 1), ev['overall'], 10, '제출 완료', ev['at']] for ev in evaluations])
insert('match_evaluation_score', ['evaluation_id', 'item_id', 'sport_id', 'score', 'observation_status'],
       [[ref('@de', ev['n'] + 1), Raw('@di_%s_%d' % (ev['room']['sport'], i)), Raw('@ds_' + ev['room']['sport']), sc, 'OBSERVED']
        for ev in evaluations for i, sc in enumerate(ev['scores'])])

photo_case = ' '.join("WHEN %s THEN %s" % (lit(SPORT_NAME[s]), lit(PHOTO[s])) for s in SPORT_NAME)
w("""-- 7. 매칭방 사진. 사전 확인을 통과한 DB(이미 적용된 DB 포함)에서만 바꾼다.
UPDATE social_match sm JOIN sport s ON s.sport_id = sm.sport_id
   SET sm.photo_url = CASE s.name %s END
 WHERE (sm.photo_url IS NULL OR sm.photo_url LIKE '/uploads/matches/seed/%%')
   AND s.name IN (%s)
   AND (@demo_ready = 1 OR @demo_already_applied = 1);
""" % (photo_case, ', '.join(lit(n) for n in SPORT_NAME.values())))

demo_title = "'^[가-힣]+ [월화수목금토일]요일 (오전|오후|저녁) (풋살|테니스|배드민턴|농구|족구) [0-9]+:[0-9]+$'"
w("""COMMIT;

-- 확인: 이 파일로 넣은 행 수 (이미 적용된 DB에서도 같은 값이 나와야 한다)
SELECT (SELECT COUNT(*) FROM app_user WHERE email LIKE 'demo-%%@spoit.test') AS demo_users,
       (SELECT COUNT(*) FROM club WHERE club_name IN (%s) AND deleted_at IS NULL) AS demo_clubs,
       (SELECT COUNT(*) FROM social_match sm JOIN app_user u ON u.user_id = sm.created_by
         WHERE u.email LIKE 'facility-__@spoit.test' AND sm.start_at >= '2026-11-01' AND sm.title REGEXP %s) AS demo_matches,
       (SELECT COUNT(*) FROM match_application a JOIN app_user u ON u.user_id = a.applicant_user_id WHERE u.email LIKE 'demo-%%@spoit.test') AS demo_applications,
       (SELECT COUNT(*) FROM social_match WHERE photo_url IS NULL OR photo_url LIKE '/uploads/matches/seed/%%') AS rooms_without_photo;
-- 기대값: demo_users %d, demo_clubs %d, demo_matches %d, demo_applications %d 이상(데모 회원이 앱에서 신청하면 늘어남), rooms_without_photo 0
""" % (club_names, demo_title, len(all_users), len(clubs), len(rooms), len(applications)))

sys.stdout.buffer.write('\n'.join(out).encode('utf-8'))
sys.stderr.write('rooms=%d (future %d, past %d) users=%d clubs=%d applications=%d participants=%d evaluations=%d\n' % (
    len(rooms), sum(1 for r in rooms if r['kind'] != 'past'), sum(1 for r in rooms if r['kind'] == 'past'),
    len(all_users), len(clubs), len(applications), len(participants), len(evaluations)))
