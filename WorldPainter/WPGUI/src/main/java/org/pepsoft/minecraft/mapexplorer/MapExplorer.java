/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.minecraft.mapexplorer;

import org.pepsoft.worldpainter.util.MinecraftUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import java.awt.*;
import java.io.File;

/**
 *
 * @author pepijn
 */
public class MapExplorer {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Minecraft Map Explorer");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            MapTreeModel treeModel = new MapTreeModel();
            File minecraftDir = MinecraftUtil.findMinecraftDir();
            File defaultDir;
            if (minecraftDir != null) {
                defaultDir = new File(minecraftDir, "saves");
            } else {
                defaultDir = new File(System.getProperty("user.home"));
            }
            JTree tree = new JTree(treeModel);
            tree.setRootVisible(false);
            tree.setCellRenderer(new MapTreeCellRenderer());
            JScrollPane scrollPane = new JScrollPane(tree);
            tree.expandPath(treeModel.getPath(defaultDir));
            tree.scrollPathToVisible(treeModel.getPath(defaultDir));
            tree.addTreeExpansionListener(new TreeExpansionListener() {
                @Override
                public void treeExpanded(TreeExpansionEvent event) {
                    if (programmaticChange) {
                        return;
                    }
                    Object node = event.getPath().getLastPathComponent();
                    if ((! treeModel.isLeaf(node)) && (treeModel.getChildCount(node) == 1)) {
                        programmaticChange = true;
                        try {
                            tree.expandPath(event.getPath().pathByAddingChild(treeModel.getChild(node, 0)));
                        } finally {
                            programmaticChange = false;
                        }
                    }
                }

                @Override public void treeCollapsed(TreeExpansionEvent event) {}

                private boolean programmaticChange;
            });
            frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
            frame.setSize(1024, 768);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}