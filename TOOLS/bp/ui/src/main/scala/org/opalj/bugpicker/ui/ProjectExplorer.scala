/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package bugpicker
package ui

import java.io.File
import java.io.FilenameFilter
import java.net.URL
import scala.collection.mutable.Map
import org.opalj.ai.AIResult
import org.opalj.ai.AITracer
import org.opalj.ai.BaseAI
import org.opalj.ai.Domain
import org.opalj.ai.common.DomainRegistry
import org.opalj.ai.common.XHTML
import org.opalj.br.ClassFile
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.analyses.Project
import org.opalj.bugpicker.ui.codeview.JumpToProblemListener
import org.opalj.bugpicker.ui.codeview.SourceFileWrapper
import org.opalj.bugpicker.ui.dialogs.ChooseDomainDialog
import org.opalj.bugpicker.ui.dialogs.DialogStage
import org.opalj.bugpicker.ui.dialogs.WebViewStage
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control.ContextMenu
import scalafx.scene.control.MenuItem
import scalafx.scene.control.TreeCell
import scalafx.scene.control.TreeView
import scalafx.scene.image.Image
import scalafx.scene.image.ImageView
import scalafx.scene.layout.Priority
import scalafx.scene.web.WebView
import scalafx.stage.Stage
import org.opalj.ai.domain.RecordDefUse

/**
 * Given a TreeView and a Project[URL] creates a visual representation of the project structure
 *
 * @author David Becker
 */
class ProjectExplorer(
        tv: TreeView[ProjectExplorerData],
        stage: Stage) {

    private[this] val projectPackageStructure: scala.collection.mutable.Map[String, ProjectExplorerTreeItem] = scala.collection.mutable.Map.empty
    private[this] val libraryPackageStructure: scala.collection.mutable.Map[String, ProjectExplorerTreeItem] = scala.collection.mutable.Map.empty
    private[this] val projectClassFileStructure: scala.collection.mutable.Map[ProjectExplorerTreeItem, ObservableBuffer[ClassFile]] = scala.collection.mutable.Map.empty
    private[this] val libraryClassFileStructure: scala.collection.mutable.Map[ProjectExplorerTreeItem, ObservableBuffer[ClassFile]] = scala.collection.mutable.Map.empty
    private[this] var project: Project[URL] = null
    private[this] var sources: Seq[File] = Seq.empty

    tv.cellFactory = (v: TreeView[ProjectExplorerData]) ⇒ new javafx.scene.control.TreeCell[ProjectExplorerData] {
        override def updateItem(item: ProjectExplorerData, empty: Boolean) = {
            super.updateItem(item, empty)
            if (!empty) {
                item match {
                    case m: ProjectExplorerMethodData ⇒ {
                        val method: Method = m.method.get
                        val classFile: ClassFile = m.classFile.get
                        val sc = new MenuItem("Show Source code") {
                            onAction = showSourceCode(classFile, method)
                        }
                        val bc = new MenuItem("Show Bytecode") {
                            onAction = showBytecode(classFile, method)
                        }
                        setContextMenu(if (!m.isAbstract.apply)
                            new ContextMenu(
                            sc,
                            bc,
                            new MenuItem("Run abstract interpretation") {
                                onAction = { e: ActionEvent ⇒
                                    val dia = new ChooseDomainDialog
                                    val chosenDomain = dia.show(stage)
                                    if (chosenDomain.isDefined) {
                                        runAbstractInterpretation(
                                            chosenDomain.get,
                                            classFile,
                                            method)
                                    }
                                }
                            })
                        else
                            new ContextMenu(sc, bc))
                    }
                    case _ ⇒
                }
                setText(item.name.apply)
                setGraphic(getTreeItem.getGraphic)
            } else {
                setContextMenu(null)
                setText(null)
                setGraphic(null)
            }
        }
    }

    def buildProjectExplorer(
        project: Project[URL],
        sources: Seq[File],
        projectName: String): Unit = {
        this.project = project
        this.sources = sources
        val root = new ProjectExplorerTreeItem(
            ProjectExplorerData(projectName),
            new ImageView {
                image = ProjectExplorer.getImage("/org/opalj/bugpicker/ui/explorer/folder.gif")
            },
            ObservableBuffer(
                new ProjectExplorerTreeItem(
                    ProjectExplorerData("Project Files"),
                    new ImageView {
                        image = ProjectExplorer.getImage("/org/opalj/bugpicker/ui/explorer/folder.gif")
                    },
                    createPackageStructure(
                        project.projectPackages.toSeq.sorted,
                        projectPackageStructure,
                        projectClassFileStructure))))
        root.expanded = true
        if (project.libraryClassFilesCount > 0) {
            root.children ++ Seq(new ProjectExplorerTreeItem(
                ProjectExplorerData("Library Files"),
                new ImageView {
                    image = ProjectExplorer.getImage("/org/opalj/bugpicker/ui/explorer/folder.gif")
                },
                createPackageStructure(
                    project.libraryPackages.toBuffer.sorted,
                    libraryPackageStructure,
                    libraryClassFileStructure)))
            initializeClassFileStructure(
                project.allLibraryClassFiles,
                libraryPackageStructure,
                libraryClassFileStructure)
        }
        initializeClassFileStructure(
            project.allProjectClassFiles,
            projectPackageStructure,
            projectClassFileStructure)

        tv.root = root
    }

    def reset(projectName: String = ""): Unit = {
        projectPackageStructure.clear()
        libraryPackageStructure.clear()
        projectClassFileStructure.clear()
        libraryClassFileStructure.clear()
        tv.root = new ProjectExplorerTreeItem(
            ProjectExplorerData(projectName),
            new ImageView {
                image = ProjectExplorer.getImage("/org/opalj/bugpicker/ui/explorer/folder.gif")
            })
    }

    private def createPackageStructure(
        packages: Seq[String],
        packageStructure: scala.collection.mutable.Map[String, ProjectExplorerTreeItem],
        classFileStructure: scala.collection.mutable.Map[ProjectExplorerTreeItem, ObservableBuffer[ClassFile]]): ObservableBuffer[ProjectExplorerTreeItem] = {
        for (p ← ObservableBuffer.apply(packages)) yield {
            val node = new ProjectExplorerTreeItem(
                if (p == "")
                    ProjectExplorerPackageData(
                    "(default package)")
                else
                    ProjectExplorerPackageData(
                        p.replace('/', '.')),
                new ImageView {
                    image = ProjectExplorer.getImage("/org/opalj/bugpicker/ui/explorer/package.gif")
                },
                ObservableBuffer.empty,
                classFileStructure)
            packageStructure += (node.value.apply.name.apply -> node)
            node
        }
    }

    private def initializeClassFileStructure(
        classFiles: Iterable[ClassFile],
        packageStructure: scala.collection.mutable.Map[String, ProjectExplorerTreeItem],
        classFileStructure: scala.collection.mutable.Map[ProjectExplorerTreeItem, ObservableBuffer[ClassFile]]): Unit = {
        for (classFile ← classFiles) {
            val objectType = classFile.thisType
            // get the ProjectExplorerTreeItem representing the Package of the ClassFile
            val packageName = if (objectType.packageName == "") "(default package)"
            else objectType.packageName.replace('/', '.')
            val packageNode = packageStructure(packageName)
            // add a link from the ProjectExplorerTreeItem to all ClassFiles belonging to it
            classFileStructure += (packageNode -> (classFileStructure.getOrElse(packageNode, ObservableBuffer.empty) :+ classFile))
        }
    }

    private def runAbstractInterpretation(
        chosenDomain: String,
        classFile: ClassFile,
        method: Method): Unit = {
        val domain: Domain = DomainRegistry.newDomain(
            chosenDomain,
            project,
            classFile,
            method)
        val ai = new BaseAI
        val aiResult = ai.apply(classFile, method, domain)
        val aiResultXHTML = XHTML.dump(
            classFile,
            method,
            "Result of Abstract Interpretation",
            aiResult).toString
        if (domain.isInstanceOf[RecordDefUse]) {
            val defUse = domain.asInstanceOf[RecordDefUse]
            val aiDefUseView: WebView = new WebView {
                contextMenuEnabled = false
                vgrow = Priority.Always
                hgrow = Priority.Always
                engine.loadContent(defUse.dumpDefUseInfo().toString())
            }
            WebViewStage.showWebView("Result of AI[DefUse]", aiDefUseView)
        }
        val aiView: WebView = new WebView {
            contextMenuEnabled = false
            vgrow = Priority.Always
            hgrow = Priority.Always
            engine.loadContent(Messages.ABSTRACT_INTERPRETATION_RUNNING)
        }
        WebViewStage.showWebView("Abstract interpretation results", aiView)
        aiView.engine.loadContent(aiResultXHTML.toString())
    }

    private def showSourceCode(
        classFile: ClassFile,
        method: Method): ActionEvent ⇒ Unit = { e: ActionEvent ⇒
        val sourceView: WebView = new WebView {
            contextMenuEnabled = false
            vgrow = Priority.Always
            hgrow = Priority.Always
        }
        val methodId = method.name + method.descriptor.toJVMDescriptor
        val firstLineOfMethod =
            method.body.flatMap(_.firstLineNumber.map { ln ⇒
                (if (ln > 2) (ln - 2) else 0).toString
            })
        val sourceFile = findSourceFile(classFile, firstLineOfMethod)
        if (!sourceFile.isDefined) {
            val fqn = classFile.fqn
            val msg = s"Could not find source code for type $fqn."
            DialogStage.showMessage("Info", msg, stage)
        } else {
            sourceView.engine.loadContent(sourceFile.get.toXHTML.toString)

            new JumpToProblemListener(
                webview = sourceView,
                methodOption = Some(methodId),
                pcOption = None,
                lineOption = firstLineOfMethod)

            WebViewStage.showWebView("SourceCode", sourceView)
        }
    }

    private def showBytecode(
        cf: ClassFile,
        method: Method): ActionEvent ⇒ Unit = { e: ActionEvent ⇒
        val byteView: WebView = new WebView {
            contextMenuEnabled = false
            vgrow = Priority.Always
            hgrow = Priority.Always
        }
        val methodId = method.name + method.descriptor.toJVMDescriptor
        val classFile = decompileClassFile(project, cf.thisType)
        if (classFile.isDefined) {
            val content = classFile.get.toXHTML.toString
            byteView.engine.loadContent(content)
        } else {
            byteView.engine.loadContent(Messages.NO_BYTECODE_FOUND)
        }

        new JumpToProblemListener(
            webview = byteView,
            methodOption = Some(methodId),
            pcOption = None, lineOption = None)

        WebViewStage.showWebView("Bytecode", byteView)
    }

    private def findSourceFile(
        classFile: ClassFile,
        lineOption: Option[String]): Option[SourceFileWrapper] = {

        val theType: ObjectType = classFile.thisType
        val sourcePackagePath = theType.packageName

        val sourceFile: Option[File] =
            if (classFile.sourceFile.isDefined) {
                sources.toStream.map(dir ⇒
                    new File(dir, sourcePackagePath+"/"+classFile.sourceFile.get)).find(_.exists()
                )
            } else {
                val name = theType.simpleName
                val packageDir =
                    sources.toStream.map(dir ⇒
                        new File(dir, sourcePackagePath)).find(_.exists()
                    )
                packageDir.map(_.listFiles(new FilenameFilter {
                    override def accept(file: File, filename: String): Boolean =
                        filename.matches("^"+name+"\\.\\w+$")
                })(0))
            }

        if (sourceFile.isDefined && sourceFile.get.exists) {
            val wrapper = new SourceFileWrapper(sourceFile.get, lineOption.getOrElse(""))
            Some(wrapper)
        } else {
            None
        }
    }

    private def decompileClassFile(project: Project[URL], theType: ObjectType): Option[org.opalj.da.ClassFile] = {
        project.source(theType).map { url ⇒
            val inStream = url.openStream
            val cf = org.opalj.da.ClassFileReader.ClassFile(() ⇒ inStream)
            inStream.close
            cf.head
        }
    }
}

object ProjectExplorer {

    private[this] val imageCache: Map[String, Image] = Map(
        "fallback" -> new Image("/org/opalj/bugpicker/ui/explorer/error.gif"))

    def getImage(path: String): Image = {
        imageCache.getOrElse(path, {
            try {
                val img = new Image(path, true)
                imageCache += (path -> img)
                img
            } catch {
                case e: Exception ⇒ imageCache("fallback")
            }
        })
    }
}