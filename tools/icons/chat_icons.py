# Draws our chat icons (D-095): 13x13 crowns for Administrator/Owner/Developer and gems for the six donator tiers.
# Run: python3 tools/icons/chat_icons.py (writes content/gameframe/src/main/resources/pack/sprites/423/*.png).
from PIL import Image
OUT='content/gameframe/src/main/resources/pack/sprites/423/'
CROWN=[
"..X...X...X.."
,"..X..XXX..X.."
,".XXX.XXX.XXX."
,".XXX.XXX.XXX."
,".XXXXXXXXXXX."
,".XXXXXXXXXXX."
,"..XXXXXXXXX.."
,"..BBBBBBBBB.."]
GEM=[
".....XXX....."
,"....XXXXX...."
,"...XXXXXXX..."
,"..XXXXXXXXX.."
,"...XXXXXXX..."
,"....XXXXX...."
,".....XXX....."
,"......X......"]
def shade(c,f): return tuple(max(0,min(255,int(v*f))) for v in c)
def draw(pattern, colour, name, top):
    im=Image.new('RGBA',(13,13),(0,0,0,0)); px=im.load()
    h=len(pattern); y0=top
    mask=set()
    for y,row in enumerate(pattern):
        for x,ch in enumerate(row):
            if ch in 'XB': mask.add((x,y+y0))
    for (x,y) in mask:
        ch=pattern[y-y0][x]
        if ch=='B': c=shade(colour,0.55)
        else:
            # light from the top-left
            rel=(x+ (y-y0)*1.6)/ (13+h*1.6)
            c=shade(colour,1.35-0.7*rel)
        px[x,y]=c+(255,)
    for (x,y) in list(mask):
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
            n=(x+dx,y+dy)
            if 0<=n[0]<13 and 0<=n[1]<13 and n not in mask and px[n][3]==0:
                px[n]=(0,0,0,255)
    im.save(OUT+name+'.png')
icons=[('60_administrator',CROWN,(220,50,50)),('61_owner',CROWN,(160,80,255)),('62_developer',CROWN,(40,200,120)),
 ('63_donator_regular',GEM,(60,190,80)),('64_donator_super',GEM,(60,130,255)),('65_donator_extreme',GEM,(230,60,60)),
 ('66_donator_legendary',GEM,(255,176,0)),('67_donator_supreme',GEM,(180,80,255)),('68_donator_uber',GEM,(255,70,200))]
for n,p,c in icons: draw(p,c,n,2 if p is CROWN else 2)
