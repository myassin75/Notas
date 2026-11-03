package haddad.three.notas.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import haddad.three.notas.update.AppReviewLauncher
import haddad.three.notas.update.GithubAppReviewLauncher
import haddad.three.notas.update.GithubPlayInAppUpdateProgressController
import haddad.three.notas.update.GithubPlayInAppUpdateStarter
import haddad.three.notas.update.GithubPlayStoreUpdateChecker
import haddad.three.notas.update.GithubPlayUpdateSession
import haddad.three.notas.update.PlayInAppUpdateProgressController
import haddad.three.notas.update.PlayInAppUpdateStarter
import haddad.three.notas.update.PlayStoreUpdateChecker
import haddad.three.notas.update.PlayUpdateSessionHandle
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
