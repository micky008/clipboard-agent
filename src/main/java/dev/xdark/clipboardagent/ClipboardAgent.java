package dev.xdark.clipboardagent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

public final class ClipboardAgent {

	private static void patch(Instrumentation inst, AbstractTransformer transformer) {
		String className = transformer.className();
		Class<?> clipboardClass;
		try {
			clipboardClass = Class.forName(className, false, null);
		} catch (ClassNotFoundException ignored) {
			System.err.printf("Missing %s%n", className);
			return;
		}
		inst.addTransformer(transformer, true);
		try {
			inst.retransformClasses(clipboardClass);
		} catch (UnmodifiableClassException ex) {
			synchronized (System.err) {
				System.err.printf("Unable to patch %s%n", className);
				ex.printStackTrace(System.err);
			}
			System.exit(1);
		}
		if (transformer.pendingPatch) {
			System.err.printf("Transformer did not catch %s, bug?%n", className);
		} else {
			System.out.printf("Patched %s%n", className);
		}
		if (!inst.removeTransformer(transformer)) {
			System.err.println("Could not remove the transformer");
		}
	}

	private static void patchWClipboard(Instrumentation inst) {
		patch(inst, new SunClipboardTransformer());
		patch(inst, new WClipboardTransformer());
	}

	public static void agentmain(String args, Instrumentation inst) {
		patchWClipboard(inst);
		System.out.println("Applied the clipboard patch");
	}

	public static void premain(String args, Instrumentation inst) {
		agentmain(args, inst);
	}
}
