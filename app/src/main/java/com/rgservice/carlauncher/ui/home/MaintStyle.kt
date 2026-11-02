package com.rgservice.carlauncher.ui.home

/** Tarjeta de arriba a la derecha del inicio. Cada usuario elige la suya en Ajustes › Pantalla. */
enum class MaintStyle(val label: String) {
    Gauges("Medidores"),      // aceite motor y caja con medidores circulares
    List("Lista"),            // aceite motor, caja y el próximo vencimiento con barras
    Urgent("Urgente"),        // lo más próximo a cambiar, con "Lo cambié hoy"
    App("ZLink grande"),      // la tarjeta grande de la app principal (diseño original)
}
