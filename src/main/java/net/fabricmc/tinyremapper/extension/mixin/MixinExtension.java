/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2021, 2023, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper.extension.mixin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.objectweb.asm.ClassVisitor;

import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyRemapper.Builder;
import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger;
import net.fabricmc.tinyremapper.extension.mixin.common.Logger.Level;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.hard.HardTargetMixinClassVisitor;
import net.fabricmc.tinyremapper.extension.mixin.soft.SoftTargetMixinClassVisitor;

/**
 * A extension for remapping mixin annotation.
 *
 * <h2>Input filtering</h2>
 *
 * <p>The mixin extension can be applied to specific input tags by providing an input tag filter in the constructor.
 * An input with nonnull input tags is only processed if it has a tag matching the filter.
 *
 * <p>If the filter is null, all inputs will be processed.
 */
public class MixinExtension implements TinyRemapper.Extension {
	private final Logger logger;
	private final Map<Integer, Collection<Consumer<CommonData>>> tasks;
	private final Set<AnnotationTarget> targets;
	private final /* @Nullable */ Predicate<InputTag> inputTagFilter;

	public enum AnnotationTarget {
		/**
		 * The string literal in mixin annotation. E.g. Mixin, Invoker, Accessor, Inject,
		 * ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.
		 */
		SOFT,
		/**
		 * The field or method with mixin annotation. E.g. Shadow, Overwrite, Accessor,
		 * Invoker, Implements.
		 */
		HARD
	}

	/**
	 * Remap mixin annotation.
	 */
	public MixinExtension() {
		this(Level.WARN);
	}

	public MixinExtension(Logger.Level logLevel) {
		this(EnumSet.allOf(AnnotationTarget.class), logLevel);
	}

	public MixinExtension(Set<AnnotationTarget> targets) {
		this(targets, Level.WARN);
	}

	public MixinExtension(/* @Nullable */ Predicate<InputTag> inputTagFilter) {
		this(EnumSet.allOf(AnnotationTarget.class), Level.WARN, inputTagFilter);
	}

	public MixinExtension(Set<AnnotationTarget> targets, Logger.Level logLevel) {
		this(targets, logLevel, null);
	}

	public MixinExtension(Set<AnnotationTarget> targets, Logger.Level logLevel, /* @Nullable */ Predicate<InputTag> inputTagFilter) {
		this.logger = new Logger(logLevel);
		this.tasks = new ConcurrentHashMap<>();
		this.targets = targets;
		this.inputTagFilter = inputTagFilter;
	}

	@Override
	public void attach(Builder builder) {
		if (targets.contains(AnnotationTarget.HARD)) {
			builder.extraAnalyzeVisitor(new AnalyzeVisitorProvider()).extraStateProcessor(this::stateProcessor);
		}

		if (targets.contains(AnnotationTarget.SOFT)) {
			builder.extraPreApplyVisitor(new PreApplyVisitorProvider());
		}
	}

	private void stateProcessor(TrEnvironment environment) {
		CommonData data = new CommonData(environment, logger);

		for (Consumer<CommonData> task : tasks.getOrDefault(environment.getMrjVersion(), Collections.emptyList())) {
			try {
				task.accept(data);
			} catch (RuntimeException e) {
				logger.error(e.getMessage());
			}
		}
	}

	/**
	 * Hard-target: Shadow, Overwrite, Accessor, Invoker, Implements.
	 */
	private final class AnalyzeVisitorProvider implements TinyRemapper.AnalyzeVisitorProvider {
		@Override
		public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next) {
			return new HardTargetMixinClassVisitor(tasks.computeIfAbsent(mrjVersion, k -> new ConcurrentLinkedQueue<>()), next);
		}

		@Override
		public ClassVisitor insertAnalyzeVisitor(int mrjVersion, String className, ClassVisitor next, InputTag[] inputTags) {
			if (inputTagFilter == null || inputTags == null) {
				return insertAnalyzeVisitor(mrjVersion, className, next);
			} else {
				for (InputTag tag : inputTags) {
					if (inputTagFilter.test(tag)) {
						return insertAnalyzeVisitor(mrjVersion, className, next);
					}
				}

				return next;
			}
		}

		@Override
		public ClassVisitor insertAnalyzeVisitor(boolean isInput, int mrjVersion, String className, ClassVisitor next, InputTag[] inputTags) {
			if (!isInput) {
				return next;
			}

			return insertAnalyzeVisitor(mrjVersion, className, next, inputTags);
		}
	}

	/**
	 * Soft-target: Mixin, Invoker, Accessor, Inject, ModifyArg, ModifyArgs, Redirect, ModifyVariable, ModifyConstant, At, Slice.
	 */
	private final class PreApplyVisitorProvider implements TinyRemapper.ApplyVisitorProvider {
		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
			return new SoftTargetMixinClassVisitor(new CommonData(cls.getEnvironment(), logger), next);
		}

		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next, InputTag[] inputTags) {
			if (!cls.isInput()) {
				return next;
			} else if (inputTagFilter == null || inputTags == null) {
				return insertApplyVisitor(cls, next);
			} else {
				for (InputTag tag : inputTags) {
					if (inputTagFilter.test(tag)) {
						return insertApplyVisitor(cls, next);
					}
				}

				return next;
			}
		}
	}
}
