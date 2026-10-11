/**
 * Line icons (24x24, stroke) used in the sidebar and on the section cards.
 * They are stored as bare `path` elements so they can be injected into a shared
 * <svg> and inherit its colour and stroke width.
 */
export const icons = {
  rocket: '<path d="M12 15.5 8.5 12c1.2-4.6 4-7.7 8.8-8.8.6 0 1 .4 1 1C17.2 9 14.1 11.8 12 15.5Z"/><path d="M8.5 12H5l2.5-3.5h3.2M12 15.5V19l3.5-2.5v-3.2"/><path d="M6.5 16.5c-1.3.6-2 2-2 3.5 1.5 0 2.9-.7 3.5-2"/>',
  pickaxe: '<path d="M4.5 7.5C8.8 3.9 14.6 3 19.5 4.5c1.5 4.9.6 10.7-3 15"/><path d="m14.5 9.5-10 10"/>',
  cube: '<path d="M12 3 3.5 7.5v9L12 21l8.5-4.5v-9L12 3Z"/><path d="M3.5 7.5 12 12l8.5-4.5"/><path d="M12 12v9"/>',
  plug: '<path d="M9 3v4.5M15 3v4.5"/><path d="M6.5 7.5h11V11a5.5 5.5 0 0 1-11 0V7.5Z"/><path d="M12 16.5V21"/>',
  film: '<rect x="3" y="3" width="18" height="18" rx="2.5"/><path d="M7.5 3v18M16.5 3v18M3 7.5h4.5M3 12h18M3 16.5h4.5M16.5 7.5H21M16.5 16.5H21"/>',
  code: '<path d="m8.5 7.5-5 4.5 5 4.5"/><path d="m15.5 7.5 5 4.5-5 4.5"/><path d="m13.5 4.5-3 15"/>',
  lifebuoy: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3.5"/><path d="m5.6 5.6 3.9 3.9M14.5 14.5l3.9 3.9M18.4 5.6l-3.9 3.9M9.5 14.5l-3.9 3.9"/>',
  translate: '<path d="M3.5 5.5h9M8 3.5v2c0 4-2.2 7.2-5 8.5"/><path d="M5.5 9c1 2 3 3.8 5.5 4.8"/><path d="m12 20.5 4-9.5 4 9.5M13.4 17.5h5.2"/>',
  book: '<path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H19v18H5.5A1.5 1.5 0 0 1 4 19.5v-15Z"/><path d="M4 17.5A1.5 1.5 0 0 1 5.5 16H19"/>',
  disk: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2.5"/>',
  gauge: '<path d="M4 18a9 9 0 1 1 16 0"/><path d="m12 14 4-4"/>',
  shield: '<path d="M12 3 5 6v6c0 4.2 2.9 7.6 7 9 4.1-1.4 7-4.8 7-9V6l-7-3Z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4"/>',
  moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z"/>',
  monitor: '<rect x="2.5" y="4" width="19" height="12.5" rx="2.5"/><path d="M8.5 20.5h7M12 16.5v4"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  close: '<path d="m6.5 6.5 11 11M17.5 6.5l-11 11"/>',
  chevron: '<path d="m6 9.5 6 6 6-6"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  network: '<circle cx="12" cy="5" r="2.5"/><circle cx="5" cy="19" r="2.5"/><circle cx="19" cy="19" r="2.5"/><path d="M12 7.5v4M12 11.5 6.5 16.8M12 11.5l5.5 5.3"/>',
}

/** Returns the markup of a complete <svg> for the given icon. */
export function icon(name, size = 20) {
  const body = icons[name] ?? icons.book
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`
}
