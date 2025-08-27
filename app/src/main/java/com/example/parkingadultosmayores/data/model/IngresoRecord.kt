package com.example.parkingadultosmayores.data.model

data class IngresoRecord(
    val id: String,
    val placa: String,
    val tipoVehiculo: String,   // "Carro" | "Moto"
    val jornada: String,        // "Dia" | "Noche" | "Completo"
    val tarifa: Double,
    val fecha: String,          // yyyy-MM-dd
    val hora: String            // HH:mm:ss
)