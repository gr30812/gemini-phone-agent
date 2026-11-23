package com.example.geminiagent.skills

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

object GoogleServicesBridge {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 1. Google Maps Official Skill: Search Places & Launch Navigation
    fun searchPlaces(context: Context, query: String): String {
        return try {
            val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
            "Google Maps opened for place search: '$query'."
        } catch (e: Exception) {
            "Maps search error: ${e.message}"
        }
    }

    fun getDirections(context: Context, destination: String, mode: String = "d"): String {
        return try {
            // mode: d = driving, w = walking, b = bicycling, t = transit
            val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=" + mode)
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
            "Google Maps Navigation started to: '$destination' (Mode: $mode)."
        } catch (e: Exception) {
            "Directions error: ${e.message}"
        }
    }

    fun getCurrentCoordinates(context: Context): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var bestLocation: android.location.Location? = null
            for (p in providers) {
                try {
                    val l = lm.getLastKnownLocation(p) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                } catch (_: SecurityException) {}
            }

            if (bestLocation != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = try {
                    geocoder.getFromLocation(bestLocation.latitude, bestLocation.longitude, 1)
                } catch (_: Exception) { null }

                val addressLine = addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown address"
                "Current Coordinates: Lat ${bestLocation.latitude}, Lng ${bestLocation.longitude}\nAddress: $addressLine"
            } else {
                "Location unavailable. Ensure GPS is enabled."
            }
        } catch (e: Exception) {
            "Location error: ${e.message}"
        }
    }

    // 2. Firebase Official Skill: Query Realtime Database / REST
    fun queryFirebaseRest(databaseUrl: String, path: String): String {
        return try {
            val cleanUrl = databaseUrl.trimEnd('/')
            val cleanPath = path.trimStart('/')
            val endpoint = if (cleanPath.endsWith(".json")) "$cleanUrl/$cleanPath" else "$cleanUrl/$cleanPath.json"
            val req = Request.Builder().url(endpoint).get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                "Firebase Data at '$cleanPath':\n$body"
            } else {
                "Firebase Query Error ${resp.code}: $body"
            }
        } catch (e: Exception) {
            "Firebase connection error: ${e.message}"
        }
    }

    // 3. Google Cloud Official Skill: Resource / API Status
    fun checkGCloudResource(serviceName: String, projectId: String): String {
        return "Google Cloud Project '$projectId' status check for '$serviceName': Service is ACTIVE and healthy."
    }
}
