package io.github.garminaicoach

import android.app.Application
import androidx.room.Room
import io.github.garminaicoach.data.DefaultHealthRepository
import io.github.garminaicoach.data.healthconnect.HealthConnectSource
import io.github.garminaicoach.data.local.CoachDatabase
import io.github.garminaicoach.data.local.RoomHealthStore
import io.github.garminaicoach.domain.HealthRepository

class CoachApplication : Application() {
    val repository: HealthRepository by lazy {
        val database = Room.databaseBuilder(this, CoachDatabase::class.java, "coach-health.db").build()
        DefaultHealthRepository(HealthConnectSource(this), RoomHealthStore(database))
    }
}
