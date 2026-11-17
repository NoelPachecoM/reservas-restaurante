import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Encabezado } from './componentes/encabezado/encabezado';
import { ApiService } from './api/api.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Encabezado],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private readonly api = inject(ApiService);

  /** Al cargar la página se revisa con Spring que el token siga sirviendo. */
  ngOnInit(): void {
    void this.api.refrescarSesion();
  }
}
