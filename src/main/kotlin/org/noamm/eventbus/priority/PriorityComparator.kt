package org.noamm.eventbus.priority

import org.noamm.eventbus.EventListener

internal object PriorityComparator: Comparator<EventListener<*>> {
    override fun compare(o1: EventListener<*>, o2: EventListener<*>): Int =
        o1.priority.ordinal - o2.priority.ordinal
}