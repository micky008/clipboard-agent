package dev.xdark.clipboardagent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

public final class ClipboardAgent {
	static final String CLIPBOARD_CLASS = "sun.awt.windows.WClipboard";
	static final String CLIPBOARD_CLASS_INTERNAL_NAME = "sun/awt/windows/WClipboard";

	public static void agentmain(String args, Instrumentation inst) {
		Class<?> clipboardClass;
		try {
			clipboardClass = Class.forName(CLIPBOARD_CLASS, false, null);
		} catch (ClassNotFoundException ignored) {
			System.err.printf("Missing %s, not running on Windows?%n", CLIPBOARD_CLASS);
			return;
		}
		Transformer transformer = new Transformer();
		inst.addTransformer(transformer, true);
		try {
			inst.retransformClasses(clipboardClass);
		} catch (UnmodifiableClassException ex) {
			synchronized (System.err) {
				System.err.printf("Unable to patch %s%n", CLIPBOARD_CLASS);
				ex.printStackTrace(System.err);
			}
			System.exit(1);
		}
		if (transformer.pendingPatch) {
			System.err.printf("Transformer did not catch %s, bug?%n", CLIPBOARD_CLASS);
		} else {
			System.out.printf("Patched %s%n", CLIPBOARD_CLASS);
		}
		if (!inst.removeTransformer(transformer)) {
			System.err.println("Could not remove the transformer");
		}
		System.out.println("Applied the clipboard patch");
	}

	public static void premain(String args, Instrumentation inst) {
		agentmain(args, inst);
	}
}
