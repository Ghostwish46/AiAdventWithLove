package com.aichallenge.aiagentapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aichallenge.aiagentapp.agent.profile.AssistantProfile
import com.aichallenge.aiagentapp.agent.profile.ProfileAvatarKind
import org.jetbrains.compose.resources.painterResource
import aiagentapp.shared.generated.resources.Res
import aiagentapp.shared.generated.resources.avatar_default
import aiagentapp.shared.generated.resources.avatar_gojo
import aiagentapp.shared.generated.resources.avatar_jaina
import aiagentapp.shared.generated.resources.avatar_martin
import aiagentapp.shared.generated.resources.avatar_tarja

@Composable
fun ProfileAvatar(
    profile: AssistantProfile,
    size: Dp = 40.dp,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Image(
                painter = painterResource(avatarResource(profile.avatarKind)),
                contentDescription = profile.label,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        if (showLabel && profile.label.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = profile.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun avatarResource(kind: ProfileAvatarKind) = when (kind) {
    ProfileAvatarKind.GOJO -> Res.drawable.avatar_gojo
    ProfileAvatarKind.JAINA -> Res.drawable.avatar_jaina
    ProfileAvatarKind.TARJA -> Res.drawable.avatar_tarja
    ProfileAvatarKind.MARTIN -> Res.drawable.avatar_martin
    ProfileAvatarKind.DEFAULT -> Res.drawable.avatar_default
}
