/**
 * Pravi ilustracije za demo podatke (naslovne fotografije, galerije, portrete frizera i
 * portfolio) kao WebP fajlove u seed/images. Slike su originalne vektorske kompozicije,
 * bez prava trećih strana. Pokretanje: npm run images --workspace seed
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "..", "images");

interface Palette {
  deep: string;
  mid: string;
  light: string;
  accent: string;
  glow: string;
}

const PALETTES: Record<string, Palette> = {
  "atelje-lana": { deep: "#3B2C1E", mid: "#8C6A3F", light: "#E9DCC6", accent: "#C9A46A", glow: "#FFF4E0" },
  "kod-vuka": { deep: "#1F2A1C", mid: "#3F5E2A", light: "#D9DFCF", accent: "#A88B5A", glow: "#F3EEDF" },
  "studio-aurora": { deep: "#2E0F1C", mid: "#7A1F3D", light: "#F1D9DF", accent: "#D4A373", glow: "#FFF0F2" },
};

/** Deterministički pseudo-slučajni brojevi, da ponovno pokretanje da iste slike. */
function rng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xffffffff;
  };
}

function interior(p: Palette, variant: number, w = 1600, h = 1000): string {
  const rand = rng(variant * 97 + 13);
  const arches = Array.from({ length: 3 }, (_, i) => {
    const x = 220 + i * 420 + (variant % 2) * 40;
    const aw = 300;
    const ah = 560;
    return `
      <path d="M${x} ${h - 170} V${h - 170 - ah + aw / 2} A${aw / 2} ${aw / 2} 0 0 1 ${x + aw} ${h - 170 - ah + aw / 2} V${h - 170} Z"
            fill="url(#mirror)" stroke="${p.accent}" stroke-width="6" opacity="0.95"/>
      <circle cx="${x + aw / 2}" cy="${h - 170 - ah - 40}" r="16" fill="${p.glow}" opacity="0.9"/>
      <circle cx="${x + aw / 2}" cy="${h - 170 - ah - 40}" r="60" fill="url(#lamp)"/>`;
  }).join("");
  const plants = Array.from({ length: 5 }, () => {
    const cx = 80 + rand() * (w - 160);
    const r = 40 + rand() * 60;
    return `<circle cx="${cx}" cy="${h - 120 - rand() * 40}" r="${r}" fill="${p.mid}" opacity="${0.15 + rand() * 0.2}"/>`;
  }).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
    <defs>
      <linearGradient id="wall" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="${p.light}"/>
        <stop offset="1" stop-color="${p.mid}" stop-opacity="0.55"/>
      </linearGradient>
      <linearGradient id="mirror" x1="0" y1="0" x2="0.3" y2="1">
        <stop offset="0" stop-color="${p.glow}"/>
        <stop offset="0.6" stop-color="${p.light}"/>
        <stop offset="1" stop-color="${p.accent}" stop-opacity="0.6"/>
      </linearGradient>
      <radialGradient id="lamp">
        <stop offset="0" stop-color="${p.glow}" stop-opacity="0.8"/>
        <stop offset="1" stop-color="${p.glow}" stop-opacity="0"/>
      </radialGradient>
      <linearGradient id="floor" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="${p.mid}"/>
        <stop offset="1" stop-color="${p.deep}"/>
      </linearGradient>
    </defs>
    <rect width="${w}" height="${h}" fill="url(#wall)"/>
    ${arches}
    <rect y="${h - 170}" width="${w}" height="170" fill="url(#floor)"/>
    <rect y="${h - 176}" width="${w}" height="10" fill="${p.accent}" opacity="0.8"/>
    ${plants}
  </svg>`;
}

function products(p: Palette, variant: number, w = 1600, h = 1000): string {
  const rand = rng(variant * 31 + 7);
  const shelves = [300, 560, 820];
  const bottles = shelves
    .map((y) =>
      Array.from({ length: 9 }, (_, i) => {
        const bw = 70 + rand() * 40;
        const bh = 120 + rand() * 90;
        const x = 120 + i * 155 + rand() * 20;
        const tone = [p.mid, p.accent, p.deep, p.light][Math.floor(rand() * 4)];
        return `<rect x="${x}" y="${y - bh}" width="${bw}" height="${bh}" rx="${bw / 3}" fill="${tone}" opacity="0.92"/>
                <rect x="${x + bw / 2 - 12}" y="${y - bh - 30}" width="24" height="34" rx="6" fill="${p.deep}" opacity="0.8"/>`;
      }).join(""),
    )
    .join("");
  const boards = shelves.map((y) => `<rect x="60" y="${y}" width="${w - 120}" height="18" rx="9" fill="${p.deep}" opacity="0.85"/>`).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">
    <defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${p.glow}"/><stop offset="1" stop-color="${p.light}"/></linearGradient></defs>
    <rect width="${w}" height="${h}" fill="url(#bg)"/>${bottles}${boards}
  </svg>`;
}

function chair(p: Palette, w = 1600, h = 1000): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">
    <defs>
      <radialGradient id="spot" cx="0.5" cy="0.35" r="0.7">
        <stop offset="0" stop-color="${p.glow}"/><stop offset="1" stop-color="${p.mid}"/>
      </radialGradient>
      <linearGradient id="seat" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="${p.deep}"/><stop offset="1" stop-color="${p.mid}"/>
      </linearGradient>
    </defs>
    <rect width="${w}" height="${h}" fill="url(#spot)"/>
    <ellipse cx="800" cy="900" rx="420" ry="50" fill="${p.deep}" opacity="0.35"/>
    <rect x="600" y="180" width="400" height="430" rx="120" fill="url(#seat)"/>
    <rect x="520" y="520" width="560" height="170" rx="70" fill="url(#seat)"/>
    <rect x="480" y="470" width="90" height="200" rx="40" fill="${p.deep}"/>
    <rect x="1030" y="470" width="90" height="200" rx="40" fill="${p.deep}"/>
    <rect x="770" y="690" width="60" height="180" fill="${p.accent}"/>
    <ellipse cx="800" cy="880" rx="220" ry="30" fill="${p.accent}"/>
  </svg>`;
}

function portrait(p: Palette, variant: number, longHair: boolean, w = 800, h = 800): string {
  const rand = rng(variant * 53 + 3);
  const skin = ["#E8C4A2", "#D9A77F", "#C68A62", "#F0D3B8"][variant % 4];
  const hair = ["#2B1D14", "#5A3A22", "#8C5A2B", "#1A1A1A", "#B07A45"][variant % 5];
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">
    <defs>
      <radialGradient id="bg" cx="0.3" cy="0.2" r="1">
        <stop offset="0" stop-color="${p.glow}"/><stop offset="1" stop-color="${p.mid}"/>
      </radialGradient>
    </defs>
    <rect width="${w}" height="${h}" fill="url(#bg)"/>
    <circle cx="${120 + rand() * 560}" cy="${120 + rand() * 200}" r="${80 + rand() * 60}" fill="${p.light}" opacity="0.25"/>
    ${longHair ? `<path d="M250 330 Q250 170 400 165 Q550 170 550 330 L575 640 L225 640 Z" fill="${hair}"/>` : ""}
    <path d="M150 800 Q150 600 400 585 Q650 600 650 800 Z" fill="${p.deep}"/>
    <rect x="360" y="470" width="80" height="130" rx="30" fill="${skin}"/>
    <ellipse cx="400" cy="360" rx="135" ry="160" fill="${skin}"/>
    <path d="M265 330 Q270 190 400 185 Q530 190 535 330 Q500 250 400 245 Q300 250 265 330 Z" fill="${hair}"/>
    <path d="M330 640 Q400 690 470 640" stroke="${p.accent}" stroke-width="10" fill="none" opacity="0.8"/>
  </svg>`;
}

function hairWork(p: Palette, variant: number, w = 1000, h = 1000): string {
  const rand = rng(variant * 71 + 5);
  const tones = [
    ["#3A2416", "#8C5A2B", "#D9A86C"],
    ["#1C1B1A", "#4A3B2E", "#B08D57"],
    ["#5A1E2B", "#A8435A", "#E8A0A8"],
    ["#2B2B2B", "#7A7A7A", "#E0E0E0"],
    ["#4A2A12", "#C47A36", "#F2C27B"],
  ][variant % 5];
  const strands = Array.from({ length: 70 }, (_, i) => {
    const x0 = 120 + rand() * 760;
    const c1 = x0 + (rand() - 0.5) * 400;
    const c2 = x0 + (rand() - 0.5) * 400;
    const x1 = x0 + (rand() - 0.5) * 300;
    const color = tones[i % 3];
    return `<path d="M${x0} 60 C${c1} 360 ${c2} 640 ${x1} 960" stroke="${color}" stroke-width="${6 + rand() * 18}" fill="none" stroke-linecap="round" opacity="${0.5 + rand() * 0.5}"/>`;
  }).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}">
    <rect width="${w}" height="${h}" fill="${p.glow}"/>
    <rect width="${w}" height="${h}" fill="${p.light}" opacity="0.6"/>
    ${strands}
  </svg>`;
}

async function save(name: string, svg: string, width: number): Promise<void> {
  const file = join(OUT, `${name}.webp`);
  const thumb = join(OUT, `${name}_thumb.webp`);
  await mkdir(dirname(file), { recursive: true });
  const buffer = Buffer.from(svg);
  await writeFile(file, await sharp(buffer).resize({ width }).webp({ quality: 80 }).toBuffer());
  await writeFile(thumb, await sharp(buffer).resize({ width: 480 }).webp({ quality: 75 }).toBuffer());
}

/** Frizeri po salonu; true = duga kosa na ilustraciji portreta. */
const STYLISTS: Record<string, [string, boolean][]> = {
  "atelje-lana": [["stefan", false], ["ivana", true], ["nina", true], ["marko", false]],
  "kod-vuka": [["vuk", false], ["luka", false], ["aleksa", false]],
  "studio-aurora": [["jelena", true], ["sara", true]],
};

async function main(): Promise<void> {
  let n = 0;
  for (const [salonId, palette] of Object.entries(PALETTES)) {
    await save(`${salonId}/cover`, interior(palette, n), 1600);
    await save(`${salonId}/gallery-1`, chair(palette), 1600);
    await save(`${salonId}/gallery-2`, products(palette, n), 1600);
    await save(`${salonId}/gallery-3`, interior(palette, n + 1), 1600);
    let s = 0;
    for (const [stylistId, longHair] of STYLISTS[salonId]) {
      const seed = n * 10 + s;
      await save(`${salonId}/stylists/${stylistId}/portrait`, portrait(palette, seed, longHair), 800);
      await save(`${salonId}/stylists/${stylistId}/portfolio-1`, hairWork(palette, seed), 1000);
      await save(`${salonId}/stylists/${stylistId}/portfolio-2`, hairWork(palette, seed + 3), 1000);
      s++;
    }
    n++;
  }
  process.stdout.write(`Images written to ${OUT}\n`);
}

await main();
