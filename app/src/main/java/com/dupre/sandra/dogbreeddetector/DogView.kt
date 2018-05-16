package com.dupre.sandra.dogbreeddetector

interface DogView {
    fun displayDogBreed(dogBreed: String, winPercent: Float)
    fun displayError()
}