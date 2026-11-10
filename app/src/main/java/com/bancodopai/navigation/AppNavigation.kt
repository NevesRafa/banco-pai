package com.bancodopai.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bancodopai.auth.mvi.action.AuthAction
import com.bancodopai.auth.mvi.effect.AuthEffect
import com.bancodopai.auth.ui.screen.LoginScreen
import com.bancodopai.auth.ui.screen.OnboardingScreen
import com.bancodopai.auth.ui.viewmodel.AuthViewModel
import com.bancodopai.child.ui.screen.ChildHomeScreen
import com.bancodopai.core.ui.theme.BancoDoPaiTheme
import com.bancodopai.domain.model.UserRole
import com.bancodopai.parent.ui.screen.ParentHomeScreen
import org.koin.androidx.compose.koinViewModel

private object Routes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val CHILD_HOME = "child_home"
    const val PARENT_HOME = "parent_home"
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = koinViewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(authViewModel, navController) {
        authViewModel.effect.collect { effect ->
            when (effect) {
                is AuthEffect.NavigateToHome -> {
                    val route = if (effect.role == UserRole.CHILD) {
                        Routes.CHILD_HOME
                    } else {
                        Routes.PARENT_HOME
                    }
                    navController.navigateToRoot(route)
                }

                AuthEffect.NavigateToOnboarding -> {
                    navController.navigateToRoot(Routes.ONBOARDING)
                }

                AuthEffect.NavigateToLogin -> {
                    navController.navigateToRoot(Routes.LOGIN)
                }

                is AuthEffect.ShowMessage -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(viewModel = authViewModel)
            }

            composable(Routes.ONBOARDING) {
                OnboardingScreen(viewModel = authViewModel)
            }

            composable(Routes.CHILD_HOME) {
                ChildHomeScreen(
                    onSignOut = {
                        authViewModel.onAction(AuthAction.SignOut)
                    }
                )
            }

            composable(Routes.PARENT_HOME) {
                ParentHomeScreen(
                    onSignOut = {
                        authViewModel.onAction(AuthAction.SignOut)
                    }
                )
            }
        }

        if (authState.isCheckingSession) {
            SplashScreen()
        }
    }
}

private fun NavHostController.navigateToRoot(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun SplashScreen() {
    BancoDoPaiTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Banco do Pai",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
