import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun bundle(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Включаем стандартную тему оформления Android (Material 3)
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainSyncScreen(
                        onSyncNowClick = { 
                            // Действие при нажатии "Синхронизировать сейчас"
                            // Сюда мы подключим ручной вызов SyncDataWorker
                        },
                        onAutoSyncToggle = { isEnabled ->
                            // Действие при переключении тумблера автоматического режима
                            scheduleFlexibleSync(this, isEnabled)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainSyncScreen(
    onSyncNowClick: () -> Unit,
    onAutoSyncToggle: (Boolean) -> Unit
) {
    // Переменные состояния (хранят положение переключателя и текст статуса)
    var isAutoSyncEnabled by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Приложение готово к работе") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Заголовок приложения
        Text(
            text = "Samsung ➔ Simpla",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Синхронизация тренировок",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Блок статуса программы
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = statusText,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Кнопка: Синхронизировать сейчас
        Button(
            onClick = {
                statusText = "Запущен ручной перенос..."
                onSyncNowClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(text = "Синхронизировать сейчас", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Строка с переключателем автоматического режима
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Автосинхронизация",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Проверка каждые 15 минут в фоне",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            
            // Тот самый системный переключатель (Свитч)
            Switch(
                checked = isAutoSyncEnabled,
                onCheckedChange = { checked ->
                    isAutoSyncEnabled = checked
                    statusText = if (checked) {
                        "Фоновый режим включен"
                    } else {
                        "Фоновый режим выключен"
                    }
                    onAutoSyncToggle(checked)
                }
            )
        }
    }
}
