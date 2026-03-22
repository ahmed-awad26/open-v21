package com.opencontacts.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencontacts.core.ui.theme.OpenContactsTheme
import com.opencontacts.feature.contacts.ContactDetailsRoute
import com.opencontacts.feature.contacts.ContactsRoute
import com.opencontacts.feature.vaults.VaultsRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemedApp()
        }
    }
}

@Composable
private fun ThemedApp(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.appLockSettings.collectAsStateWithLifecycle()
    OpenContactsTheme(themeMode = settings.themeMode) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AppRoot(viewModel)
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val shouldShowUnlock by viewModel.shouldShowUnlock.collectAsStateWithLifecycle()
    if (shouldShowUnlock) UnlockRoute(viewModel = viewModel) else AppNavHost(viewModel)
}

@Composable
private fun AppNavHost(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val activeVaultName by viewModel.activeVaultName.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "contacts") {
        composable("contacts") {
            ContactsRoute(
                activeVaultName = activeVaultName,
                vaults = vaults,
                onOpenDetails = { navController.navigate("contact/$it") },
                onOpenWorkspace = { navController.navigate("workspace") },
                onOpenImportExport = { navController.navigate("importexport") },
                onOpenSearch = { navController.navigate("search") },
                onOpenSecurity = { navController.navigate("security") },
                onOpenBackup = { navController.navigate("backup") },
                onOpenTrash = { navController.navigate("trash") },
                onOpenVaults = { navController.navigate("vaults") },
                onSwitchVault = viewModel::switchVault,
            )
        }
        composable(route = "contact/{contactId}", arguments = listOf(navArgument("contactId") { type = NavType.StringType })) {
            ContactDetailsRoute(onBack = { navController.popBackStack() })
        }
        composable("vaults") { VaultsRoute(onBack = { navController.popBackStack() }) }
        composable("security") { SecurityRoute(onBack = { navController.popBackStack() }) }
        composable("search") { SearchRoute(onBack = { navController.popBackStack() }) }
        composable("workspace") {
            WorkspaceRoute(
                onBack = { navController.popBackStack() },
                onOpenDetails = { navController.navigate("contact/$it") },
            )
        }
        composable("backup") { BackupRoute(onBack = { navController.popBackStack() }) }
        composable("importexport") { ImportExportRoute(onBack = { navController.popBackStack() }) }
        composable("trash") { TrashRoute(onBack = { navController.popBackStack() }) }
    }
}
