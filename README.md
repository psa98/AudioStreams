# AudioStreams

A library for Android that implements work with audio in form 16-bit PCM byte streams, a standard data type for its media subsystem.

Inspired by javax.sound.sampled package, unavailable in Android.  
Provides classes for simple capture, processing, storage and playback of sampled audio data, represented as Input/Output byte streams.  This will allow you to easily store and process sound clips in memory and use third-party libraries (voice recognition, effects, encryption) that work with byte streams or 16-bit PCM short/byte arrays.  
