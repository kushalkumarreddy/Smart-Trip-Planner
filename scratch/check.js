const fs = require('fs');
const css = fs.readFileSync('src/main/resources/static/css/style.css', 'utf8');
let d = 0;
let errors = [];
const lines = css.split('\n');
lines.forEach((l, i) => {
  const clean = l.replace(/\/\*[\s\S]*?\*\//g, '');
  for (let ch of clean) {
    if (ch === '{') d++;
    else if (ch === '}') {
      d--;
      if (d < 0) {
        errors.push(`Line ${i+1}: extra closing brace`);
        d = 0;
      }
    }
  }
});
console.log('Brace depth:', d, 'Errors:', errors.length);
