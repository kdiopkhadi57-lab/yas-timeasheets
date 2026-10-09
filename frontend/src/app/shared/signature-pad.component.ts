import { Component, ElementRef, output, signal, viewChild } from '@angular/core';

/** Zone de signature à la souris / au doigt, ou import d'une image ; restitue un PNG en data URL. */
@Component({
  selector: 'app-signature-pad',
  template: `
    <div class="pad-wrap">
      <canvas #canvas width="460" height="170" (pointerdown)="start($event)" (pointermove)="move($event)"
              (pointerup)="end()" (pointerleave)="end()" aria-label="Zone de signature"></canvas>
      @if (empty()) { <span class="hint">Signez ici</span> }
    </div>
    <div class="row">
      <button type="button" class="btn sm" (click)="clear()">Effacer</button>
      <label class="btn sm">Importer une image<input type="file" accept="image/png,image/jpeg" hidden (change)="importImage($event)"></label>
      <span class="grow"></span>
      <button type="button" class="btn primary sm" [disabled]="empty()" (click)="emit()">Enregistrer la signature</button>
    </div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 8px; }
    .pad-wrap { position: relative; border: 1.5px dashed #c9ced5; border-radius: 10px; background: #fff; max-width: 460px; }
    canvas { display: block; width: 100%; height: auto; touch-action: none; cursor: crosshair; }
    .hint { position: absolute; inset: 0; display: grid; place-items: center; color: #b8bec6; pointer-events: none; font-size: 15px; }
  `,
})
export class SignaturePadComponent {
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  readonly saved = output<string>();
  readonly empty = signal(true);
  private drawing = false;

  private ctx(): CanvasRenderingContext2D {
    const c = this.canvas().nativeElement.getContext('2d')!;
    c.strokeStyle = '#1d3fbf';
    c.lineWidth = 2.6;
    c.lineCap = 'round';
    c.lineJoin = 'round';
    return c;
  }

  private point(ev: PointerEvent): [number, number] {
    const el = this.canvas().nativeElement;
    const r = el.getBoundingClientRect();
    return [((ev.clientX - r.left) * el.width) / r.width, ((ev.clientY - r.top) * el.height) / r.height];
  }

  start(ev: PointerEvent): void {
    this.drawing = true;
    (ev.target as HTMLElement).setPointerCapture?.(ev.pointerId);
    const c = this.ctx();
    c.beginPath();
    c.moveTo(...this.point(ev));
  }

  move(ev: PointerEvent): void {
    if (!this.drawing) return;
    const c = this.ctx();
    c.lineTo(...this.point(ev));
    c.stroke();
    this.empty.set(false);
  }

  end(): void { this.drawing = false; }

  clear(): void {
    const el = this.canvas().nativeElement;
    el.getContext('2d')!.clearRect(0, 0, el.width, el.height);
    this.empty.set(true);
  }

  /** Image scannée ou photo de la signature : redimensionnée dans la zone puis exportée en PNG. */
  importImage(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const img = new Image();
    img.onload = () => {
      this.clear();
      const el = this.canvas().nativeElement;
      const scale = Math.min(el.width / img.width, el.height / img.height);
      const w = img.width * scale;
      const h = img.height * scale;
      el.getContext('2d')!.drawImage(img, (el.width - w) / 2, (el.height - h) / 2, w, h);
      URL.revokeObjectURL(img.src);
      this.empty.set(false);
    };
    img.src = URL.createObjectURL(file);
  }

  emit(): void { this.saved.emit(this.canvas().nativeElement.toDataURL('image/png')); }
}
