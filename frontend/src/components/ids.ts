const ID_PATTERN = /^[a-z0-9][a-z0-9-]*$/

export const validId = (id: string) => ID_PATTERN.test(id)

/** "Uhrzeit in Berlin" becomes "uhrzeit-in-berlin": the id a new building block gets from its name. */
export function slugify(name: string) {
  return name
    .toLowerCase()
    .replace(/ä/g, 'ae')
    .replace(/ö/g, 'oe')
    .replace(/ü/g, 'ue')
    .replace(/ß/g, 'ss')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

export type Naming = { name: string; id: string; idTouched: boolean }
