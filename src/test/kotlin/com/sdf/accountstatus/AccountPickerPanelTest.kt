package com.sdf.accountstatus

import com.intellij.ui.components.ActionLink
import com.sdf.accountstatus.core.SdfAuthentication
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountPickerPanelTest {
    @Test
    fun `keeps open project action fixed in the popup footer`() {
        val panel = AccountPickerPanel({}, {}, {})

        val openAction = panel.descendants()
            .filterIsInstance<ActionLink>()
            .single { it.text == "Open project.json" }
        val footer = openAction.parent as JPanel

        assertEquals(BorderLayout.EAST, (footer.layout as BorderLayout).getConstraints(openAction))
    }

    @Test
    fun `restores search text and caret for the picker`() {
        val panel = AccountPickerPanel({}, {}, {})

        panel.restoreSearchQuery("example company administrator")

        assertEquals("example company administrator", panel.searchQuery())
    }

    @Test
    fun `places each combined account and customer group directly below its section`() {
        val panel = AccountPickerPanel({}, {}, {})
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                SdfAuthentication(
                    "example-current",
                    "123456_SB1: Acme Example Corp [Administrator]"
                ),
                SdfAuthentication(
                    "example-production",
                    "123456: Acme Example Corp [Administrator]"
                )
            ),
            currentAuthenticationId = "example-current"
        )

        val root = panel.buildTree(model)
        val section = root.onlyChild()
        val combinedGroup = section.onlyChild()

        assertEquals(2, combinedGroup.childCount)
        assertEquals(0, (combinedGroup.getChildAt(0) as DefaultMutableTreeNode).childCount)
        assertEquals(0, (combinedGroup.getChildAt(1) as DefaultMutableTreeNode).childCount)
    }

    private fun Container.descendants(): Sequence<Component> = sequence {
        components.forEach { component ->
            yield(component)
            if (component is Container) yieldAll(component.descendants())
        }
    }

    private fun DefaultMutableTreeNode.onlyChild(): DefaultMutableTreeNode {
        assertEquals(1, childCount)
        return getChildAt(0) as DefaultMutableTreeNode
    }
}
