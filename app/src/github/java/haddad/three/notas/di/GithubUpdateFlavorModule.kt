package three.haddad.smartnotas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import three.haddad.smartnotas.update.AppReviewLauncher
import three.haddad.smartnotas.update.GithubAppReviewLauncher
import three.haddad.smartnotas.update.GithubPlayInAppUpdateProgressController
import three.haddad.smartnotas.update.GithubPlayInAppUpdateStarter
import three.haddad.smartnotas.update.GithubPlayStoreUpdateChecker
import three.haddad.smartnotas.update.GithubPlayUpdateSession
import three.haddad.smartnotas.update.PlayInAppUpdateProgressController
import three.haddad.smartnotas.update.PlayInAppUpdateStarter
import three.haddad.smartnotas.update.PlayStoreUpdateChecker
import three.haddad.smartnotas.update.PlayUpdateSessionHandle
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GithubUpdateFlavorModule {
    @Binds
    @Singleton
    abstract fun bindPlayUpdateSession(handle: GithubPlayUpdateSession): PlayUpdateSessionHandle

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateStarter(starter: GithubPlayInAppUpdateStarter): PlayInAppUpdateStarter

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateProgressController(
        controller: GithubPlayInAppUpdateProgressController,
    ): PlayInAppUpdateProgressController

    @Binds
    @Singleton
    abstract fun bindPlayStoreUpdateChecker(checker: GithubPlayStoreUpdateChecker): PlayStoreUpdateChecker

    @Binds
    @Singleton
    abstract fun bindAppReviewLauncher(launcher: GithubAppReviewLauncher): AppReviewLauncher
}
