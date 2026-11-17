import { Directive, ElementRef, HostListener, inject } from '@angular/core';

/**
 * Campo de teléfono: solo acepta números y máximo 10 dígitos.
 * Uso: <input appTelefono [(ngModel)]="telefono" />
 */
@Directive({
  selector: 'input[appTelefono]',
  host: { inputmode: 'numeric', maxlength: '10', autocomplete: 'tel', placeholder: '10 dígitos' },
})
export class Telefono {
  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;

  @HostListener('input')
  limpiar(): void {
    const limpio = this.campo.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== this.campo.value) {
      this.campo.value = limpio;
      // Se avisa de nuevo para que el formulario se quede con el valor ya limpio
      this.campo.dispatchEvent(new Event('input', { bubbles: true }));
    }
  }
}
