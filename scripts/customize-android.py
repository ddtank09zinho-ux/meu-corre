"""Personaliza o projeto Android gerado pelo Capacitor: ícones, splash,
notificação do GPS, assinatura e versão. Roda no GitHub Actions após `npx cap add android`."""
import json, os, re, glob
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'android/app/src/main/res')
BG, YEL, INK = (17, 20, 24), (245, 184, 0), (26, 20, 0)

def font(px):
    for f in ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
              '/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf']:
        if os.path.exists(f):
            return ImageFont.truetype(f, px)
    return ImageFont.load_default(size=px)

def diamond(im, cx, cy, s):
    d = ImageDraw.Draw(im)
    d.polygon([(cx, cy - s), (cx + s, cy), (cx, cy + s), (cx - s, cy)], fill=YEL)
    f = font(max(8, int(s * 0.62))); bb = d.textbbox((0, 0), 'MC', font=f)
    d.text((cx - (bb[2] - bb[0]) / 2 - bb[0], cy - (bb[3] - bb[1]) / 2 - bb[1]), 'MC', fill=INK, font=f)

def icon(size, rnd=False):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if rnd: d.ellipse([0, 0, size - 1, size - 1], fill=BG)
    else: d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.18, fill=BG)
    diamond(im, size / 2, size / 2, size * 0.36); return im

# Ícones do app web (pasta web/icons) — usados também no site
os.makedirs(os.path.join(ROOT, 'web/icons'), exist_ok=True)
for name, size, rnd in [('icon-192.png', 192, False), ('icon-512.png', 512, False), ('apple-touch-icon.png', 180, False)]:
    im = Image.new('RGB', (size, size), BG); diamond(im, size / 2, size / 2, size * 0.44)
    im.save(os.path.join(ROOT, 'web/icons', name))
im = Image.new('RGB', (512, 512), BG); diamond(im, 256, 256, 512 * 0.36); im.save(os.path.join(ROOT, 'web/icons/maskable-512.png'))

if os.path.isdir(RES):
    for k, m in {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}.items():
        icon(int(48 * m)).save(f'{RES}/mipmap-{k}/ic_launcher.png')
        icon(int(48 * m), True).save(f'{RES}/mipmap-{k}/ic_launcher_round.png')
        fg = Image.new('RGBA', (int(108 * m),) * 2, (0, 0, 0, 0)); diamond(fg, 54 * m, 54 * m, 27 * m)
        fg.save(f'{RES}/mipmap-{k}/ic_launcher_foreground.png')
    for f in glob.glob(f'{RES}/drawable*/splash.png'):
        w, h = Image.open(f).size; im = Image.new('RGB', (w, h), BG); diamond(im, w / 2, h / 2, min(w, h) * 0.14); im.save(f)
    open(f'{RES}/values/ic_launcher_background.xml', 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#111418</color>\n</resources>\n')
    open(f'{RES}/drawable/ic_tracking.xml', 'w').write(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" '
        'android:viewportWidth="24" android:viewportHeight="24">\n'
        '    <path android:fillColor="#FFFFFFFF" android:pathData="M12,2L22,12L12,22L2,12Z"/>\n</vector>\n')
    p = f'{RES}/values/strings.xml'; s = open(p).read()
    if 'capacitor_background_geolocation' not in s:
        s = s.replace('</resources>',
            '    <string name="capacitor_background_geolocation_notification_channel_name">Contagem de km</string>\n'
            '    <string name="capacitor_background_geolocation_notification_icon">drawable/ic_tracking</string>\n'
            '    <string name="capacitor_background_geolocation_notification_color">#F5B800</string>\n</resources>')
        open(p, 'w').write(s)

    # Versão e assinatura
    pkg = json.load(open(os.path.join(ROOT, 'package.json')))
    ver = pkg['version']; maj, mi, pa = [int(x) for x in ver.split('.')]
    code = int(os.environ.get('GITHUB_RUN_NUMBER', '0')) + maj * 10000 + mi * 100 + pa
    p = os.path.join(ROOT, 'android/app/build.gradle'); s = open(p).read()
    s = re.sub(r'versionCode \d+', f'versionCode {code}', s)
    s = re.sub(r'versionName "[^"]*"', f'versionName "{ver}"', s)
    if 'signingConfigs' not in s:
        s = s.replace('    buildTypes {\n        release {\n',
            '    signingConfigs {\n        release {\n'
            '            if (System.getenv("KEYSTORE_PASSWORD")) {\n'
            '                storeFile file("release.jks")\n'
            '                storePassword System.getenv("KEYSTORE_PASSWORD")\n'
            '                keyAlias "meucorre"\n'
            '                keyPassword System.getenv("KEYSTORE_PASSWORD")\n'
            '            }\n        }\n    }\n'
            '    buildTypes {\n        release {\n'
            '            if (System.getenv("KEYSTORE_PASSWORD")) { signingConfig signingConfigs.release }\n', 1)
    open(p, 'w').write(s)
    assert 'signingConfigs' in s, 'não consegui configurar a assinatura'
    print(f'Android personalizado: versão {ver} (código {code})')
else:
    print('Ícones web gerados (sem projeto Android)')
