package dev.xdark.clipboardagent;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

// Helper class to unlock 'this' object monitor only once.
final class MonitorLocker {
	private final int slot;
	private final MethodVisitor mv;

	MonitorLocker(int slot, MethodVisitor mv) {
		this.slot = slot;
		this.mv = mv;
	}

	void init() {
		// unlocked = false;
		MethodVisitor mv = this.mv;
		mv.visitInsn(ICONST_0);
		mv.visitVarInsn(ISTORE, slot);
		// synchronized(this)
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(MONITORENTER);
	}

	void unlock() {
		MethodVisitor mv = this.mv;
		Label skip = new Label();
		// if (unlocked) goto skip;
		mv.visitVarInsn(ILOAD, slot);
		mv.visitJumpInsn(IFNE, skip);
		// monitorexit(this)
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(MONITOREXIT);
		// ulnocked = true;
		mv.visitInsn(ICONST_1);
		mv.visitVarInsn(ISTORE, slot);
		mv.visitLabel(skip);
	}
}
