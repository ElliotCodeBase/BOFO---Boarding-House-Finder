"""Builds the BOFO mascot from basic shapes only (rounded rectangles, circles, one triangle, one arc) as SVG and PNG."""
import math
from PIL import Image, ImageDraw

P, PD, A, T, W, TINT = "#6C3FC5", "#4A2A8F", "#FFB400", "#1F1A2E", "#FFFFFF", "#EDE4FA"
AD = "#E39B00"   # darker amber for the hat brim and chimney

def rot(pt, deg, c):
    a = math.radians(deg); x, y = pt[0]-c[0], pt[1]-c[1]
    return (c[0] + x*math.cos(a) - y*math.sin(a), c[1] + x*math.sin(a) + y*math.cos(a))   # SVG: +deg = clockwise

def bez(p0, p1, p2, n=24):
    return [((1-t)**2*p0[0]+2*(1-t)*t*p1[0]+t*t*p2[0], (1-t)**2*p0[1]+2*(1-t)*t*p1[1]+t*t*p2[1]) for t in [i/n for i in range(n+1)]]

def shapes(expr):
    s = []
    R = lambda x,y,w,h,r,f,o=1,rt=None: s.append(("rect",x,y,w,h,r,f,o,rt))
    C = lambda cx,cy,r,f,o=1: s.append(("circle",cx,cy,r,f,o))
    # feet (no arms: one clean silhouette)
    R(60,156,30,16,8,PD); R(110,156,30,16,8,PD)
    # hat: chimney + roof behind the head, brim over its top edge
    R(119,18,13,24,4,AD)
    s.append(("roof",[(57,48),(100,16),(143,48)],A,8))
    # body (rounded rectangle) + dark face screen + soft glass highlight + amber status light
    R(40,50,120,112,32,P)
    R(56,70,88,68,22,W)
    R(66,77,36,5,2.5,TINT)
    R(86,146,28,6,3,A)
    R(38,45,124,14,7,AD)
    # eyes / mouth by expression
    if expr == "thinking":
        R(80,90,14,18,7,T); R(110,90,14,18,7,T)                      # eyes glance up and to the side
        R(88,119,24,5,2.5,T)                                          # flat mouth
        C(168,40,5,A); C(180,26,7,A); R(166,2,30,20,10,A)             # thought bubble
    elif expr == "waving":
        s.append(("arc", bez((72,104),(81,88),(90,104)), T, 5))        # happy ^ ^ eyes (greeting)
        s.append(("arc", bez((110,104),(119,88),(128,104)), T, 5))
        s.append(("mouthfill", bez((84,113),(100,136),(116,113)), T))
    else:
        R(76,92,14,22,7,T); R(110,92,14,22,7,T)
        s.append(("arc", bez((86,122),(100,134),(114,122)), T, 5))
    return s

def to_svg(shapes_, size=512, bg=None, wrap=None):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">']
    if bg: out.append(f'<rect width="200" height="200" rx="44" fill="{bg}"/>')
    if wrap: out.append(f'<g transform="{wrap}">')
    for sh in shapes_:
        k = sh[0]
        if k == "rect":
            _, x,y,w,h,r,f,o,rt = sh
            tr = f' transform="rotate({rt[0]} {rt[1]} {rt[2]})"' if rt else ""
            out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{f}" opacity="{o}"{tr}/>')
        elif k == "circle":
            _, cx,cy,r,f,o = sh; out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r}" fill="{f}" opacity="{o}"/>')
        elif k == "roof":
            pts = " ".join(f"{x},{y}" for x,y in sh[1]); out.append(f'<polygon points="{pts}" fill="{sh[2]}" stroke="{sh[2]}" stroke-width="{sh[3]}" stroke-linejoin="round"/>')
        elif k == "arc":
            d = "M"+" L".join(f"{x:.1f} {y:.1f}" for x,y in sh[1]); out.append(f'<path d="{d}" fill="none" stroke="{sh[2]}" stroke-width="{sh[3]}" stroke-linecap="round" stroke-linejoin="round"/>')
        elif k == "mouthfill":
            d = "M"+" L".join(f"{x:.1f} {y:.1f}" for x,y in sh[1])+" Z"; out.append(f'<path d="{d}" fill="{sh[2]}" stroke="{sh[2]}" stroke-width="4" stroke-linejoin="round"/>')
    if wrap: out.append('</g>')
    out.append('</svg>'); return "\n".join(out)

def to_png(shapes_, px=512, bg=None, scale=1.0, dy=0):
    SS = 4; n = px*SS; k = n/200
    im = Image.new("RGBA", (n, n), (0,0,0,0))
    if bg:
        m = Image.new("RGBA",(n,n),(0,0,0,0)); ImageDraw.Draw(m).rounded_rectangle((0,0,n-1,n-1), radius=44*k, fill=bg); im = Image.alpha_composite(im, m)
    def T_(p):  # optional centre scale for the icon
        return ((100+(p[0]-100)*scale)*k, (92+dy+(p[1]-92)*scale)*k)
    S = lambda v: v*scale*k
    for sh in shapes_:
        kind = sh[0]; layer = Image.new("RGBA",(n,n),(0,0,0,0)); d = ImageDraw.Draw(layer)
        if kind == "rect":
            _, x,y,w,h,r,f,o,rt = sh
            x0,y0 = T_((x,y)); d.rounded_rectangle((x0,y0,x0+S(w),y0+S(h)), radius=S(r), fill=f)
            if rt: c = T_((rt[1],rt[2])); layer = layer.rotate(-rt[0], center=c, resample=Image.BICUBIC)
            if o < 1: layer.putalpha(layer.getchannel("A").point(lambda a: int(a*o)))
        elif kind == "circle":
            _, cx,cy,r,f,o = sh; c = T_((cx,cy)); d.ellipse((c[0]-S(r),c[1]-S(r),c[0]+S(r),c[1]+S(r)), fill=f)
            if o < 1: layer.putalpha(layer.getchannel("A").point(lambda a: int(a*o)))
        elif kind == "roof":
            pts = [T_(p) for p in sh[1]]; w = S(sh[3]); d.polygon(pts, fill=sh[2])
            for i in range(3):
                a, b = pts[i], pts[(i+1)%3]; d.line([a,b], fill=sh[2], width=int(w)); d.ellipse((a[0]-w/2,a[1]-w/2,a[0]+w/2,a[1]+w/2), fill=sh[2])
        elif kind == "arc":
            pts = [T_(p) for p in sh[1]]; w = S(sh[3]); d.line(pts, fill=sh[2], width=int(w), joint="curve")
            for p in (pts[0], pts[-1]): d.ellipse((p[0]-w/2,p[1]-w/2,p[0]+w/2,p[1]+w/2), fill=sh[2])
        elif kind == "mouthfill":
            pts = [T_(p) for p in sh[1]]; d.polygon(pts, fill=sh[2]); d.line(pts+[pts[0]], fill=sh[2], width=int(S(4)), joint="curve")
        im = Image.alpha_composite(im, layer)
    return im.resize((px,px), Image.LANCZOS)

if __name__ == "__main__":
    for e in ("happy","thinking","waving"):
        sh = shapes(e)
        open(f"bofo-{e}.svg","w").write(to_svg(sh)); to_png(sh).save(f"bofo-{e}.png")
    sh = shapes("happy")
    open("bofo-app-icon.svg","w").write(to_svg(sh, bg=TINT, wrap="translate(100 102) scale(0.98) translate(-100 -92)"))
    to_png(sh, 512, bg=TINT, scale=0.98, dy=10).save("bofo-app-icon.png")
    # contact sheet for review
    sheet = Image.new("RGBA",(1536,512),(248,245,253,255))
    for i,e in enumerate(("happy","thinking","waving")):
        sheet.alpha_composite(Image.open(f"bofo-{e}.png"),(i*512,0))
    sheet.save("preview.png")
    print("ok")
