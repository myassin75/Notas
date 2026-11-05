package haddad.three.notas.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import haddad.three.notas.crypto.CryptoStorage
import haddad.three.notas.crypto.DefaultKeyStoreManager
import haddad.three.notas.crypto.KeyStoreManager
import haddad.three.notas.crypto.MekManager
import haddad.three.notas.crypto.RecoverySetupManager
import haddad.three.notas.crypto.SharedPrefsCryptoStorage
import haddad.three.notas.data.auth.AuthRepository
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {
    @Provides
    @Singleton
    fun provideKeyStoreManager(): KeyStoreManager = DefaultKeyStoreManager()

    @Provides
    @Singleton
    fun provideCryptoStorage(
        @ApplicationContext context: Context,
        authRepositoryProvider: Provider<AuthRepository>,
    ): CryptoStorage =
        SharedPrefsCryptoStorage(
            context = context,
            accountIdProvider = { authRepositoryProvider.get().currentUser?.uid },
        )

    @Provides
    @Singleton
    fun provideMekManager(
        keyStoreManager: KeyStoreManager,
        cryptoStorage: CryptoStorage,
        authRepositoryProvider: Provider<AuthRepository>,
        @ApplicationContext context: Context,
    ): MekManager =
        MekManager(
            keyStoreManager = keyStoreManager,
            storage = cryptoStorage,
            context = context,
            accountIdProvider = { authRepositoryProvider.get().currentUser?.uid },
        )

    @Provides
    @Singleton
    fun provideRecoverySetupManager(
        mekManager: MekManager,
        @ApplicationContext context: Context,
    ): RecoverySetupManager = RecoverySetupManager(mekManager, context)
}
