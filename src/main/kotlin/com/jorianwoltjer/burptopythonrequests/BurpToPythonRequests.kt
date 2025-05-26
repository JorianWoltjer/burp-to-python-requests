package com.jorianwoltjer.burptopythonrequests

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JMenuItem
import kotlin.jvm.optionals.getOrNull

@Suppress("unused")
class BurpToPythonRequests : BurpExtension {
    override fun initialize(api: MontoyaApi?) {
        if (api == null) {
            return
        }

        api.extension().setName("To Python Requests")
        api.logging().logToOutput("Starting To Python Requests extension...")

        api.userInterface().registerContextMenuItemsProvider(MyContextMenuProvider(api))
    }
}

class MyContextMenuProvider(private val api: MontoyaApi): ContextMenuItemsProvider {
    override fun provideMenuItems(event: ContextMenuEvent?): List<Component?>? {
        if (event == null || (event.messageEditorRequestResponse().isEmpty && event.selectedRequestResponses().isEmpty())) {
            api.logging().logToOutput("No request found in context menu event.")
            return null
        }
        val requestResponse = event.messageEditorRequestResponse().getOrNull()?.requestResponse()
            ?: event.selectedRequestResponses().firstOrNull()
            ?: return null

        val item = JMenuItem("Copy request")
        item.addActionListener {
            api.logging().logToOutput("Copying requests...")
            val pythonCode = PythonRequestsConverter.convert(requestResponse)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(pythonCode), null)
        }
        val item2 = JMenuItem("Copy template")
        item2.addActionListener {
            api.logging().logToOutput("Copying template...")
            val pythonCode = PythonTemplateGenerator.generate(requestResponse)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(pythonCode), null)
        }
        return mutableListOf(item, item2)
    }
}