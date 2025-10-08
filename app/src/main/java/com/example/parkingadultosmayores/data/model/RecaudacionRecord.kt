// file: data/model/RecaudacionRecord.kt
package com.example.parkingadultosmayores.data.model

data class RecaudacionRecord(
    val id: String,            // UUID del registro de recaudación
    val idIngreso: String,     // id del Ingreso que dio origen al cobro
    val placa: String,
    val tipoVehiculo: String,  // "Carro" | "Moto"
    val jornada: String,       // "Dia" | "Noche" | "Diario" | "Nocturno" | "Completo"
    val fecha: String,         // yyyy-MM-dd  (DÍA DEL COBRO) — mantenemos nombre para compatibilidad
    val horaEntrada: String,   // HH:mm:ss
    val horaSalida: String,    // HH:mm:ss
    val monto: Double,

    // NUEVOS (opcionales, para reimpresión fiel). Gson soporta ausencia sin romper:
    val fechaEntrada: String? = null,   // yyyy-MM-dd (del Ingreso original)
    val horasCobradas: Int? = null      // para el texto del ticket
)
