import { Component, ElementRef, OnDestroy, afterNextRender, computed, inject, input, signal } from '@angular/core';

/**
 * Graphiques du tableau de bord, en SVG / HTML sans bibliothèque.
 * Règles communes : marques fines, 2 px d'espace entre segments, texte en encre neutre (jamais la couleur
 * de la série), légende dès deux séries, survol avec infobulle.
 */

export interface Segment { key: string; label: string; value: number; color: string; }
export interface Series { name: string; color: string; values: number[]; }

function fmt(v: number): string {
  return Number.isInteger(v) ? String(v) : v.toLocaleString('fr-FR', { maximumFractionDigits: 1 });
}

/** Largeur réelle de l'hôte, pour dessiner le SVG au pixel près (texte non déformé). */
function useWidth(initial: number) {
  const host = inject(ElementRef<HTMLElement>);
  const width = signal(initial);
  let observer: ResizeObserver | undefined;
  afterNextRender(() => {
    const el = host.nativeElement as HTMLElement;
    width.set(Math.max(240, el.clientWidth || initial));
    observer = new ResizeObserver(([e]) => width.set(Math.max(240, Math.round(e.contentRect.width))));
    observer.observe(el);
  });
  return { width, disconnect: () => observer?.disconnect() };
}

/** Plus petit pas « rond » donnant environ `count` graduations. */
function niceTicks(max: number, count = 4): number[] {
  if (max <= 0) return [0, 1];
  const raw = max / count;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  const step = [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw;
  const ticks: number[] = [];
  for (let v = 0; v <= max + step * 0.001; v += step) ticks.push(Math.round(v * 100) / 100);
  if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step);
  return ticks;
}

// ---------------------------------------------------------------- Anneau

@Component({
  selector: 'app-donut',
  template: `
    <div class="donut">
      <svg [attr.viewBox]="'0 0 ' + size + ' ' + size" [attr.width]="size" [attr.height]="size" role="img" [attr.aria-label]="ariaLabel()">
        @if (total() === 0) {
          <circle [attr.cx]="size / 2" [attr.cy]="size / 2" [attr.r]="radius" fill="none" stroke="var(--border)" [attr.stroke-width]="thickness" />
        }
        @for (a of arcs(); track a.key) {
          <path [attr.d]="a.d" [attr.fill]="a.color" class="arc" [class.dim]="hover() && hover() !== a.key"
                (mouseenter)="hover.set(a.key)" (mouseleave)="hover.set(null)" />
        }
        <text [attr.x]="size / 2" [attr.y]="size / 2 - 2" text-anchor="middle" class="c-val">{{ center().value }}</text>
        <text [attr.x]="size / 2" [attr.y]="size / 2 + 16" text-anchor="middle" class="c-lbl">{{ center().label }}</text>
      </svg>
      <ul class="legend">
        @for (s of segments(); track s.key) {
          <li [class.dim]="hover() && hover() !== s.key" (mouseenter)="hover.set(s.key)" (mouseleave)="hover.set(null)">
            <span class="sw" [style.background]="s.color"></span>
            <span class="grow">{{ s.label }}</span>
            <strong>{{ s.value }}</strong>
            <span class="pct">{{ pct(s.value) }}</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .donut { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
    svg { flex: none; }
    .arc { transition: opacity .15s; cursor: default; }
    .dim { opacity: .35; }
    .c-val { font-size: 26px; font-weight: 700; fill: var(--text); }
    .c-lbl { font-size: 12px; fill: var(--muted); }
    .legend { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; flex: 1; min-width: 160px; }
    .legend li { display: flex; align-items: center; gap: 8px; font-size: 13px; padding: 2px 4px; border-radius: 6px; transition: opacity .15s; }
    .sw { width: 10px; height: 10px; border-radius: 3px; flex: none; }
    .grow { flex: 1; }
    .pct { color: var(--muted); font-size: 12px; min-width: 38px; text-align: right; font-variant-numeric: tabular-nums; }
  `,
})
export class DonutComponent {
  readonly segments = input.required<Segment[]>();
  readonly centerLabel = input('total');
  readonly size = 168;
  readonly thickness = 22;
  readonly radius = (this.size - this.thickness) / 2;
  readonly hover = signal<string | null>(null);

  readonly total = computed(() => this.segments().reduce((s, x) => s + x.value, 0));
  readonly center = computed(() => {
    const h = this.segments().find((s) => s.key === this.hover());
    return h ? { value: fmt(h.value), label: h.label } : { value: fmt(this.total()), label: this.centerLabel() };
  });
  readonly ariaLabel = computed(() => this.segments().map((s) => `${s.label} : ${s.value}`).join(', '));

  /** Arcs pleins avec un espace de 2 px (en angle) entre segments. */
  readonly arcs = computed(() => {
    const total = this.total();
    if (!total) return [];
    const c = this.size / 2;
    const ro = c - 1;
    const ri = ro - this.thickness;
    const visible = this.segments().filter((s) => s.value > 0);
    const gap = visible.length > 1 ? 2 / ro : 0;
    let angle = -Math.PI / 2;
    return visible.map((s) => {
      const sweep = (s.value / total) * Math.PI * 2;
      const a0 = angle + gap / 2;
      const a1 = angle + sweep - gap / 2;
      angle += sweep;
      if (visible.length === 1) {
        // Anneau complet : deux demi-arcs.
        const d = `M ${c} ${c - ro} A ${ro} ${ro} 0 1 1 ${c} ${c + ro} A ${ro} ${ro} 0 1 1 ${c} ${c - ro} Z `
          + `M ${c} ${c - ri} A ${ri} ${ri} 0 1 0 ${c} ${c + ri} A ${ri} ${ri} 0 1 0 ${c} ${c - ri} Z`;
        return { key: s.key, color: s.color, d };
      }
      const large = a1 - a0 > Math.PI ? 1 : 0;
      const p = (r: number, a: number) => `${c + r * Math.cos(a)} ${c + r * Math.sin(a)}`;
      const d = `M ${p(ro, a0)} A ${ro} ${ro} 0 ${large} 1 ${p(ro, a1)} L ${p(ri, a1)} A ${ri} ${ri} 0 ${large} 0 ${p(ri, a0)} Z`;
      return { key: s.key, color: s.color, d };
    });
  });

  pct(v: number): string {
    const t = this.total();
    return t ? Math.round((v / t) * 100) + ' %' : '';
  }
}

// ---------------------------------------------------------------- Barres horizontales empilées

export interface BarRow { id: string | number; label: string; sub?: string; segments: Segment[]; note?: string; }

@Component({
  selector: 'app-bar-list',
  template: `
    @if (showLegend() && legend().length > 1) {
      <ul class="legend">
        @for (l of legend(); track l.key) { <li><span class="sw" [style.background]="l.color"></span>{{ l.label }}</li> }
      </ul>
    }
    <div class="rows">
      @for (r of rows(); track r.id) {
        <div class="row" (mouseenter)="hover.set(r.id)" (mouseleave)="hover.set(null)">
          <div class="lbl">
            <div class="name" [title]="r.label">{{ r.label }}</div>
            @if (r.sub) { <div class="sub">{{ r.sub }}</div> }
          </div>
          <div class="track">
            <div class="bar" [style.width.%]="pct(sum(r))">
              @for (s of r.segments; track s.key) {
                @if (s.value > 0) { <div class="seg" [style.flex-grow]="s.value" [style.background]="s.color"></div> }
              }
            </div>
            <span class="val">{{ fmt(sum(r)) }}{{ unit() }}</span>
          </div>
          @if (r.note) { <div class="note">{{ r.note }}</div> }
          @if (hover() === r.id) {
            <div class="tip">
              <strong>{{ r.label }}</strong>
              @for (s of r.segments; track s.key) {
                <div class="tip-line"><span class="sw" [style.background]="s.color"></span><span class="grow">{{ s.label }}</span><b>{{ fmt(s.value) }}{{ unit() }}</b></div>
              }
            </div>
          }
        </div>
      } @empty {
        <p class="empty-s">{{ emptyText() }}</p>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .legend { list-style: none; display: flex; flex-wrap: wrap; gap: 14px; margin: 0 0 12px; padding: 0; font-size: 12px; color: var(--muted); }
    .legend li { display: flex; align-items: center; gap: 6px; }
    .sw { width: 10px; height: 10px; border-radius: 3px; display: inline-block; flex: none; }
    .rows { display: flex; flex-direction: column; gap: 4px; }
    .row { position: relative; display: grid; grid-template-columns: minmax(110px, 30%) 1fr auto; align-items: center; gap: 12px; padding: 6px 6px; border-radius: 8px; }
    .row:hover { background: var(--surface-2); }
    .lbl { min-width: 0; }
    .name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .sub { font-size: 11.5px; color: var(--muted); }
    .track { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .bar { display: flex; gap: 2px; height: 14px; min-width: 2px; }
    .seg { height: 100%; min-width: 3px; }
    .seg:first-child { border-radius: 0; }
    .seg:last-child { border-radius: 0 4px 4px 0; }
    .val { font-size: 12.5px; font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .note { font-size: 12px; color: var(--danger); font-weight: 600; white-space: nowrap; }
    .tip { position: absolute; z-index: 5; left: 30%; top: calc(100% - 2px); background: var(--surface); border: 1px solid var(--border); box-shadow: 0 6px 20px rgba(16,34,46,.14); border-radius: 8px; padding: 8px 10px; min-width: 200px; font-size: 12.5px; pointer-events: none; }
    .tip-line { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
    .grow { flex: 1; }
    .empty-s { color: var(--muted); font-size: 13px; padding: 12px 0; margin: 0; }
  `,
})
export class BarListComponent {
  readonly rows = input.required<BarRow[]>();
  readonly unit = input('');
  readonly emptyText = input('Aucune donnée.');
  /** false quand chaque ligne a sa propre couleur et que l'étiquette de ligne suffit. */
  readonly showLegend = input(true);
  readonly hover = signal<string | number | null>(null);
  readonly fmt = fmt;

  readonly max = computed(() => Math.max(1, ...this.rows().map((r) => this.sum(r))));
  readonly legend = computed(() => {
    const seen = new Map<string, Segment>();
    this.rows().forEach((r) => r.segments.forEach((s) => seen.has(s.key) || seen.set(s.key, s)));
    return [...seen.values()];
  });

  sum(r: BarRow): number { return r.segments.reduce((s, x) => s + x.value, 0); }
  pct(v: number): number { return (v / this.max()) * 88; }
}

// ---------------------------------------------------------------- Courbes

@Component({
  selector: 'app-line-chart',
  template: `
    <ul class="legend">
      @for (s of series(); track s.name) {
        <li><span class="sw" [style.background]="s.color"></span>{{ s.name }}</li>
      }
    </ul>
    <div class="wrap" (mouseleave)="hover.set(null)">
      <svg [attr.width]="width()" [attr.height]="height()" role="img" [attr.aria-label]="ariaLabel()"
           (mousemove)="move($event)">
        @for (t of ticks(); track t) {
          <line [attr.x1]="pad.l" [attr.x2]="width() - pad.r" [attr.y1]="y(t)" [attr.y2]="y(t)" class="grid" [class.base]="t === 0" />
          <text [attr.x]="pad.l - 8" [attr.y]="y(t) + 4" text-anchor="end" class="axis">{{ t }}</text>
        }
        @for (l of labels(); track $index) {
          @if (showLabel($index)) {
            <text [attr.x]="x($index)" [attr.y]="height() - 6" text-anchor="middle" class="axis">{{ l }}</text>
          }
        }
        @if (hover() !== null) {
          <line [attr.x1]="x(hover()!)" [attr.x2]="x(hover()!)" [attr.y1]="pad.t" [attr.y2]="height() - pad.b" class="cross" />
        }
        @for (s of series(); track s.name) {
          @if (area() && $first) { <path [attr.d]="areaPath(s)" [attr.fill]="s.color" opacity=".1" /> }
          <path [attr.d]="path(s)" fill="none" [attr.stroke]="s.color" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
          <circle [attr.cx]="x(s.values.length - 1)" [attr.cy]="yAt(s, s.values.length - 1)" r="4" [attr.fill]="s.color" stroke="var(--surface)" stroke-width="2" />
          @if (hover() !== null) {
            <circle [attr.cx]="x(hover()!)" [attr.cy]="yAt(s, hover()!)" r="4.5" [attr.fill]="s.color" stroke="var(--surface)" stroke-width="2" />
          }
        }
      </svg>
      @if (hover() !== null) {
        <div class="tip" [style.left.px]="tipLeft()">
          <strong>{{ tipTitle()(hover()!) }}</strong>
          @for (s of series(); track s.name) {
            <div class="tip-line"><span class="sw" [style.background]="s.color"></span><span class="grow">{{ s.name }}</span><b>{{ s.values[hover()!] }}</b></div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .legend { list-style: none; display: flex; flex-wrap: wrap; gap: 14px; margin: 0 0 8px; padding: 0; font-size: 12px; color: var(--muted); }
    .legend li { display: flex; align-items: center; gap: 6px; }
    .sw { width: 10px; height: 10px; border-radius: 3px; display: inline-block; flex: none; }
    .wrap { position: relative; }
    svg { display: block; overflow: visible; }
    .grid { stroke: #eceef1; stroke-width: 1; }
    .grid.base { stroke: #c9ced5; }
    .axis { font-size: 11px; fill: var(--muted); font-variant-numeric: tabular-nums; }
    .cross { stroke: #c9ced5; stroke-width: 1; }
    .tip { position: absolute; top: 4px; transform: translateX(-50%); background: var(--surface); border: 1px solid var(--border); box-shadow: 0 6px 20px rgba(16,34,46,.14); border-radius: 8px; padding: 8px 10px; min-width: 170px; font-size: 12.5px; pointer-events: none; }
    .tip-line { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
    .grow { flex: 1; }
  `,
})
export class LineChartComponent implements OnDestroy {
  readonly series = input.required<Series[]>();
  readonly labels = input.required<string[]>();
  /** Titre de l'infobulle pour l'index survolé. */
  readonly tipTitle = input<(i: number) => string>((i) => this.labels()[i]);
  /** Lavis sous la première série. */
  readonly area = input(false);
  readonly height = input(220);
  readonly pad = { l: 36, r: 12, t: 10, b: 24 };
  readonly hover = signal<number | null>(null);
  private readonly w = useWidth(600);
  readonly width = this.w.width;

  readonly ticks = computed(() => niceTicks(Math.max(1, ...this.series().flatMap((s) => s.values))));
  readonly ariaLabel = computed(() => this.series().map((s) => `${s.name} : ${s.values.join(', ')}`).join(' ; '));
  readonly tipLeft = computed(() => {
    const h = this.hover();
    if (h === null) return 0;
    return Math.min(Math.max(this.x(h), 90), this.width() - 90);
  });

  ngOnDestroy(): void { this.w.disconnect(); }

  x(i: number): number {
    const n = Math.max(1, this.labels().length - 1);
    return this.pad.l + (i / n) * (this.width() - this.pad.l - this.pad.r);
  }
  y(v: number): number {
    const max = this.ticks()[this.ticks().length - 1] || 1;
    return this.pad.t + (1 - v / max) * (this.height() - this.pad.t - this.pad.b);
  }
  yAt(s: Series, i: number): number { return this.y(s.values.at(i) ?? 0); }
  path(s: Series): string { return s.values.map((v, i) => `${i ? 'L' : 'M'} ${this.x(i)} ${this.y(v)}`).join(' '); }
  areaPath(s: Series): string {
    const n = s.values.length - 1;
    return `${this.path(s)} L ${this.x(n)} ${this.y(0)} L ${this.x(0)} ${this.y(0)} Z`;
  }
  showLabel(i: number): boolean {
    const n = this.labels().length;
    const every = Math.ceil(n / Math.max(2, Math.floor(this.width() / 56)));
    return i % every === 0 || i === n - 1;
  }
  move(ev: MouseEvent): void {
    const rect = (ev.currentTarget as SVGElement).getBoundingClientRect();
    const px = ev.clientX - rect.left;
    const n = this.labels().length;
    if (!n) return;
    const step = (this.width() - this.pad.l - this.pad.r) / Math.max(1, n - 1);
    this.hover.set(Math.min(n - 1, Math.max(0, Math.round((px - this.pad.l) / step))));
  }
}

// ---------------------------------------------------------------- Colonnes empilées (100 %)

export interface Column { label: string; title: string; total: number; segments: Segment[]; }

@Component({
  selector: 'app-column-chart',
  template: `
    @if (legend().length > 1) {
      <ul class="legend">
        @for (l of legend(); track l.key) { <li><span class="sw" [style.background]="l.color"></span>{{ l.label }}</li> }
      </ul>
    }
    <div class="plot" (mouseleave)="hover.set(null)">
      <div class="yaxis"><span>100 %</span><span>50 %</span><span>0</span></div>
      <div class="cols">
        <div class="gridline" style="top:0"></div>
        <div class="gridline" style="top:50%"></div>
        @for (c of columns(); track c.label; let i = $index) {
          <div class="col" (mouseenter)="hover.set(i)">
            <div class="stack">
              @for (s of c.segments; track s.key) {
                @if (s.value > 0) { <div class="seg" [style.height.%]="c.total ? (s.value / c.total) * 100 : 0" [style.background]="s.color"></div> }
              }
            </div>
            <div class="x">{{ c.label }}</div>
            @if (hover() === i) {
              <div class="tip" [class.right]="i > columns().length / 2">
                <strong>{{ c.title }}</strong>
                @for (s of c.segments; track s.key) {
                  <div class="tip-line"><span class="sw" [style.background]="s.color"></span><span class="grow">{{ s.label }}</span><b>{{ s.value }} / {{ c.total }}</b></div>
                }
              </div>
            }
          </div>
        } @empty {
          <p class="empty-s">{{ emptyText() }}</p>
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .legend { list-style: none; display: flex; flex-wrap: wrap; gap: 14px; margin: 0 0 10px; padding: 0; font-size: 12px; color: var(--muted); }
    .legend li { display: flex; align-items: center; gap: 6px; }
    .sw { width: 10px; height: 10px; border-radius: 3px; display: inline-block; flex: none; }
    .plot { display: flex; gap: 8px; height: 200px; }
    .yaxis { display: flex; flex-direction: column; justify-content: space-between; font-size: 11px; color: var(--muted); padding-bottom: 22px; text-align: right; min-width: 34px; }
    .yaxis span { transform: translateY(-50%); }
    .yaxis span:last-child { transform: translateY(50%); }
    .cols { position: relative; flex: 1; display: flex; align-items: stretch; gap: 6px; border-bottom: 1px solid #c9ced5; margin-bottom: 22px; }
    .gridline { position: absolute; left: 0; right: 0; border-top: 1px solid #eceef1; }
    .col { position: relative; flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; min-width: 0; }
    .col:hover { background: rgba(16,34,46,.03); }
    .stack { width: min(24px, 70%); height: 100%; display: flex; flex-direction: column-reverse; gap: 2px; }
    .seg { width: 100%; }
    .seg:last-child { border-radius: 4px 4px 0 0; }
    .x { position: absolute; bottom: -20px; font-size: 11px; color: var(--muted); white-space: nowrap; }
    .tip { position: absolute; z-index: 5; bottom: 40%; left: 50%; background: var(--surface); border: 1px solid var(--border); box-shadow: 0 6px 20px rgba(16,34,46,.14); border-radius: 8px; padding: 8px 10px; min-width: 190px; font-size: 12.5px; pointer-events: none; }
    .tip.right { left: auto; right: 50%; }
    .tip-line { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
    .grow { flex: 1; }
    .empty-s { color: var(--muted); font-size: 13px; margin: auto; }
  `,
})
export class ColumnChartComponent {
  readonly columns = input.required<Column[]>();
  readonly emptyText = input('Aucune donnée.');
  readonly hover = signal<number | null>(null);
  readonly legend = computed(() => this.columns()[0]?.segments ?? []);
}
