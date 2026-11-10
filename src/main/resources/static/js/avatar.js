import { esc } from './dom.js';

const glyphCtx = document.createElement('canvas').getContext('2d');
const glyphCache = new Map();

export const byName = (track) => (track.addedById === 'owner' ? 'You' : track.addedBy || '');

export const initial = (text) => (Array.from(text.trim())[0] || '?').toUpperCase();

function glyphShift(ch, weight, family) {
  const key = `${ch}|${weight}|${family}`;
  if (glyphCache.has(key)) return glyphCache.get(key);
  glyphCtx.font = `${weight} 200px ${family}`;
  const glyph = glyphCtx.measureText(ch);
  const capital = glyphCtx.measureText('H');
  const dx = glyph.width / 2 - (glyph.actualBoundingBoxRight - glyph.actualBoundingBoxLeft) / 2;
  const belowCenter = (capital.fontBoundingBoxAscent - capital.fontBoundingBoxDescent) / 2
    - (capital.actualBoundingBoxAscent - capital.actualBoundingBoxDescent) / 2;
  const shift = { x: dx / 200, y: -belowCenter / 200 };
  glyphCache.set(key, shift);
  return shift;
}

export const gsvg = (ch, box, fontSize) =>
  `<svg class="gsvg" viewBox="0 0 ${box} ${box}" aria-hidden="true"><text data-box="${box}" data-fs="${fontSize}" `
  + `x="${box / 2}" y="${box / 2}" text-anchor="middle" style="font-size:${fontSize}px">${esc(ch)}</text></svg>`;

export function centerGlyphs() {
  document.querySelectorAll('.gsvg text').forEach((el) => {
    const box = Number(el.dataset.box);
    const fontSize = Number(el.dataset.fs);
    const ch = el.textContent;
    const style = getComputedStyle(el);
    glyphCtx.font = `${style.fontWeight} 200px ${style.fontFamily}`;
    const capHeight = glyphCtx.measureText('H').actualBoundingBoxAscent / 200;
    const shiftX = /^[\p{L}\p{N}]$/u.test(ch) ? glyphShift(ch, style.fontWeight, style.fontFamily).x : 0;
    el.setAttribute('x', (box / 2 + shiftX * fontSize).toFixed(3));
    el.setAttribute('y', (box / 2 + (capHeight * fontSize) / 2).toFixed(3));
  });
}

export function init() {
  const recenter = () => {
    glyphCache.clear();
    centerGlyphs();
  };
  document.fonts.ready.then(recenter);
  document.fonts.addEventListener?.('loadingdone', recenter);
}
