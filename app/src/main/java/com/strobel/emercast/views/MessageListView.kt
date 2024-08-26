package com.strobel.emercast.views

import android.icu.text.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strobel.emercast.R
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListView(viewModel: MessageListViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Emercast")
            }, colors = topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary
            ))
        }
    ) {
        padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            itemsIndexed(viewModel.messageList) {
                index, message ->
                Row(modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp)
                        .background(if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier
                            .width(70.dp)
                            .padding(horizontal = 20.dp)) {
                            if(message.directlyReceived)
                                Icon(painter = painterResource(R.drawable.cloud), contentDescription = "Cloud", tint = MaterialTheme.colorScheme.primary)
                            else Icon(painter = painterResource(R.drawable.bluetooth), contentDescription = "Bluetooth", tint = MaterialTheme.colorScheme.primary)
                        }
                        Column (modifier = Modifier.fillMaxHeight()) {
                            Text(message.title, fontSize = 16.sp)
                            Text(fontSize = 13.sp, text = "Created: " + DateFormat.getDateTimeInstance().format(Date.from(Instant.ofEpochSecond(message.created))))
                            Text(fontSize = 13.sp, text = "Received: " + DateFormat.getDateTimeInstance().format(Date.from(Instant.ofEpochSecond(message.received))))
                        }
                    }
                    Button(onClick = { viewModel.deleteMessage(message) }) {
                        Icon(painter = painterResource(R.drawable.delete), contentDescription = "Delete", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/*
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EmercastTheme {
        MessageListView(
            listOf(
                BroadcastMessage(
                "test-id",
                1720446193,
                1720446193,
                1720446193,
                1,
                1720446193,
                -48.1351f,
                11.5820f,
                100,
                "test",
                1,
                "Message Title",
                "Message Content"),
                BroadcastMessage(
                    "test-id2",
                    1720446193,
                    1720446193,
                    1720446193,
                    0,
                    1720446193,
                    -48.1351f,
                    11.5820f,
                    100,
                    "test",
                    1,
                    "Message Title2",
                    "Message Content2")
            )
        )
    }
}*/
