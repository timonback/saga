package demo.repository

import demo.domain.BusinessEntity
import demo.domain.Identifiable

class DataStore<T : BusinessEntity> {
    private val data: MutableMap<String, T> = mutableMapOf()

    fun get(key: Identifiable): T? {
        return data[key.getId()]
    }

    fun put(key: T) {
        data[key.getId()] = key
    }

    fun remove(key: Identifiable) {
        data.remove(key.getId())
    }

    fun clear() {
        data.clear()
    }
}