package viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 一個 ViewModel 的工廠類別，用於建立 YouBikeViewModel 並傳遞 Application 給它。
 */
class ViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(YouBikeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return YouBikeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}