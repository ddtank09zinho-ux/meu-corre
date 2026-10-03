// Copia o app web (pasta ../web) para www e inclui o Capacitor (ponte com o Android)
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const src = path.join(root, 'web'), dst = path.join(root, 'www');
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
const cap = path.join(root, 'node_modules/@capacitor/core/dist/capacitor.js');
if (fs.existsSync(cap)) {
  fs.copyFileSync(cap, path.join(dst, 'capacitor.js'));
  const idx = path.join(dst, 'index.html');
  const html = fs.readFileSync(idx, 'utf8').replace('<script>\n(function(){', '<script src="capacitor.js"></script>\n<script>\n(function(){');
  fs.writeFileSync(idx, html);
}
console.log('web copiado para www');
