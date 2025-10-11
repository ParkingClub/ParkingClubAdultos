package com.example.parkingadultosmayores.bluetooth

object PrinterConfig {
    // Mac de impresora Portatil (ajusta según tu impresora emparejada)
    const val MAC = "00:AA:11:BB:22:CC"
    // Mac de impresora Omar
    //const val MAC = "DC:0D:30:A0:78:E6"
    // Mac de impresora Trabajo Viejita
    //const val MAC = "DC:0D:30:CC:8D:5A"
    // Mac de Don Alonso
    //const val MAC = "DC:0D:30:A0:77:C4"
    // Mac Sunmi Nueva
    //const val MAC = "00:11:22:33:44:55"

    // Datos del local / sucursal
    const val SUCU_NAME = "Parqueadero Mono Jorge"
    const val UBICACION = "Sucre N2-98 Y Flores"
    const val EMAIL = "email: achavez0920@hotmail.com"
    const val PHONE = "Cel: 0999893662 - 0993954389"

    /**
     * Descripción/leyenda que aparecerá en el TICKET DE INGRESO.
     * Puedes editar libremente este texto sin tocar otras pantallas.
     */
    val INFO_INGRESO: String = """ Horario: 
        Lun-Sáb 8:30 a 18:30
        Dom 8:00 a 16:00
        No nos responsabilizamos por 
        objetos olvidados.
        Presente este comprobante al 
        retirar su vehículo.
        Costo por ticket perdido: ${'$'}10,00
 ${PHONE}
    """.trimIndent()
}
