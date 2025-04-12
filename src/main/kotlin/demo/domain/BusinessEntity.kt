package demo.domain

interface Identifiable {
    fun getId(): String
}

interface BusinessEntity : Identifiable