package haddad.three.notas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import haddad.three.notas.update.AppReviewLauncher
import haddad.three.notas.update.PlayInAppUpdateProgressController
import haddad.three.notas.update.PlayInAppUpdateSession
import haddad.three.notas.update.PlayInAppUpdateStarter
import haddad.three.notas.update.PlayStoreAppReviewLauncher
import haddad.three.notas.update.PlayStorePlayInAppUpdateCoordinator
import haddad.three.notas.update.PlayStoreUpdateChecker
import haddad.three.notas.update.PlayStoreUpdateCheckerImpl
import haddad.three.notas.update.PlayUpdateSessionHandle
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayStoreUpdateFlavorModule {
    @Binds
    @Singleton
    abstract fun bindPlayUpdateSession(handle: PlayInAppUpdateSession): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(starter: PlayStorePlayInAppUpdateCoordinator): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        controller: PlayStorePlayInAppUpdateCoordinator,
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindPlayStoreUpdateChecker(checker: PlayStoreUpdateCheckerImpl): PlayStoreUpdateChecker

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(launcher: PlayStoreAppReviewLauncher): AppReviewLauncher
}
