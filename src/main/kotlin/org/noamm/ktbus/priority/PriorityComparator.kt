package org.noamm.ktbus.priority

import org.noamm.ktbus.EventListener

internal class PriorityComparator : Comparator<EventListener<*>> {
    override fun compare(o1: EventListener<*>, o2: EventListener<*>): Int =
        o1.priority.ordinal - o2.priority.ordinal
}