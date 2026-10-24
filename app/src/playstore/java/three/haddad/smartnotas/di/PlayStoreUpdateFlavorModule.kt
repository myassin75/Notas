package three.haddad.smartnotas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import three.haddad.smartnotas.update.AppReviewLauncher
import three.haddad.smartnotas.update.PlayInAppUpdateProgressController
import three.haddad.smartnotas.update.PlayInAppUpdateSession
import three.haddad.smartnotas.update.PlayInAppUpdateStarter
import three.haddad.smartnotas.update.PlayStoreAppReviewLauncher
import three.haddad.smartnotas.update.PlayStorePlayInAppUpdateCoordinator
import three.haddad.smartnotas.update.PlayStoreUpdateChecker
import three.haddad.smartnotas.update.PlayStoreUpdateCheckerImpl
import three.haddad.smartnotas.update.PlayUpdateSessionHandle
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
