package tk.hacker1024.epimetheus

import org.greenrobot.eventbus.EventBus

internal val eventBus by lazy { EventBus.builder().addIndex(EventBusIndex()).build() }

class MusicServiceEvent(val disconnect: Boolean)