/** Dates manipulées en 'YYYY-MM-DD' (heure locale), comme le backend. */
export function iso(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export function parse(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(s: string, n: number): string {
  const d = parse(s);
  d.setDate(d.getDate() + n);
  return iso(d);
}

export function today(): string {
  return iso(new Date());
}

const DAY_FMT = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
const SHORT_FMT = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });

export function longDay(s: string): string {
  const t = DAY_FMT.format(parse(s));
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export function shortDate(s: string | null | undefined): string {
  return s ? SHORT_FMT.format(parse(s.substring(0, 10))) : '';
}

const SPAN_DAY = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric' });
const SPAN_DAY_MONTH = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
const SPAN_DATE = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long' });
const SPAN_FULL = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });

/**
 * Période d'une fiche en clair : « Du lundi 5 au vendredi 9 octobre 2026 » pour un rapport hebdo
 * (période samedi → vendredi, affichée du lundi au vendredi), « Du 20 septembre au 19 octobre 2026 » pour le mensuel.
 */
export function reportSpan(r: { type: 'HEBDO' | 'MENSUEL'; periodStart: string; periodEnd: string }): string {
  if (r.type === 'MENSUEL') return `Du ${SPAN_DATE.format(parse(r.periodStart))} au ${SPAN_FULL.format(parse(r.periodEnd))}`;
  const monday = parse(addDays(r.periodStart, 2));
  const friday = parse(addDays(r.periodStart, 6));
  const from = (monday.getMonth() === friday.getMonth() ? SPAN_DAY : SPAN_DAY_MONTH).format(monday);
  return `Du ${from} au ${SPAN_DAY_MONTH.format(friday)} ${friday.getFullYear()}`;
}
