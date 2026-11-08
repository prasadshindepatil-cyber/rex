package com.rexai.controller.agent.vision;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class RexScreenActionEngine {
    private final AccessibilityService accessibilityService;

    public RexScreenActionEngine(AccessibilityService accessibilityService) {
        this.accessibilityService = accessibilityService;
    }

    public boolean clickByText(String targetText) {
        if (accessibilityService == null) return false;
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if (rootNode == null) return false;

        AccessibilityNodeInfo targetNode = findNodeByText(rootNode, targetText);
        if (targetNode != null) {
            boolean clicked = performClickOnNode(targetNode);
            targetNode.recycle();
            rootNode.recycle();
            return clicked;
        }
        rootNode.recycle();
        return false;
    }

    public boolean clickById(String resourceId) {
        if (accessibilityService == null) return false;
        AccessibilityNodeInfo rootNode = accessibilityService.getRootInActiveWindow();
        if (rootNode == null) return false;

        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) {
                    boolean clicked = performClickOnNode(node);
                    node.recycle();
                    rootNode.recycle();
                    return clicked;
                }
            }
        }
        rootNode.recycle();
        return false;
    }

    public boolean clickAtCoordinates(int x, int y) {
        if (accessibilityService == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            return false;
        }
        android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x, y);
        builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50));
        
        final boolean[] success = {false};
        accessibilityService.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(android.accessibilityservice.GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                success[0] = true;
            }
            @Override
            public void onCancelled(android.accessibilityservice.GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                success[0] = false;
            }
        }, null);
        return success[0];
    }

    public boolean performGlobalAction(int action) {
        if (accessibilityService == null) return false;
        return accessibilityService.performGlobalAction(action);
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.getText() != null && node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
            return node;
        }
        if (node.getContentDescription() != null && node.getContentDescription().toString().toLowerCase().contains(text.toLowerCase())) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findNodeByText(child, text);
            if (result != null) {
                if (child != result) {
                    try { child.recycle(); } catch (Exception ignored) {}
                }
                return result;
            }
            if (child != null) {
                child.recycle();
            }
        }
        return null;
    }

    private boolean performClickOnNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                boolean clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                return clicked;
            }
            AccessibilityNodeInfo oldParent = parent; // temporary holder
            parent = parent.getParent();
            try { oldParent.recycle(); } catch (Exception ignored) {}
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
