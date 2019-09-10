/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2010-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.annotations.generator;

import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.json.simple.JSONValue;

@SupportedAnnotationTypes({ KrollJSONGenerator.Kroll_proxy, KrollJSONGenerator.Kroll_module })
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({ KrollJSONGenerator.PROPERTY_JSON_PACKAGE, KrollJSONGenerator.PROPERTY_JSON_FILE })
@SuppressWarnings("unchecked")
public class KrollJSONGenerator extends AbstractProcessor
{

	protected static final String TAG = "KrollBindingGen";

	// define these here so we can avoid the titanium dependency chicken/egg problem
	protected static final String Kroll_package = "org.appcelerator.kroll";
	protected static final String Kroll_annotation = Kroll_package + ".annotations.Kroll";

	protected static final String Kroll_argument = Kroll_annotation + ".argument";
	protected static final String Kroll_constant = Kroll_annotation + ".constant";
	protected static final String Kroll_getProperty = Kroll_annotation + ".getProperty";
	protected static final String Kroll_inject = Kroll_annotation + ".inject";
	protected static final String Kroll_method = Kroll_annotation + ".method";
	protected static final String Kroll_module = Kroll_annotation + ".module";
	protected static final String Kroll_property = Kroll_annotation + ".property";
	protected static final String Kroll_proxy = Kroll_annotation + ".proxy";
	protected static final String Kroll_setProperty = Kroll_annotation + ".setProperty";
	protected static final String Kroll_topLevel = Kroll_annotation + ".topLevel";
	protected static final String Kroll_dynamicApis = Kroll_annotation + ".dynamicApis";
	protected static final String Kroll_interceptor = Kroll_annotation + ".interceptor";
	protected static final String Kroll_onAppCreate = Kroll_annotation + ".onAppCreate";

	protected static final String KrollPromise = "org.appcelerator.kroll.KrollPromise";
	protected static final String KrollInvocation = "org.appcelerator.kroll.KrollInvocation";
	protected static final String KrollConverter = Kroll_package + ".KrollConverter";
	protected static final String KrollNativeConverter = Kroll_package + ".KrollNativeConverter";
	protected static final String KrollJavascriptConverter = Kroll_package + ".KrollJavascriptConverter";
	protected static final String KrollModule = Kroll_package + ".KrollModule";

	// this needs to mirror Kroll.DEFAULT_NAME
	protected static final String DEFAULT_NAME = "__default_name__";
	protected static final String Kroll_DEFAULT = Kroll_annotation + ".DEFAULT";

	protected static final String PROPERTY_JSON_PACKAGE = "kroll.jsonPackage";
	protected static final String PROPERTY_JSON_FILE = "kroll.jsonFile";
	protected static final String PROPERTY_PROJECT_DIR = "kroll.projectDir";
	protected static final String DEFAULT_JSON_PACKAGE = "org.appcelerator.titanium.gen";
	protected static final String DEFAULT_JSON_FILE = "bindings.json";

	// we make these generic because they may be initialized by JSON
	protected Map<Object, Object> properties = new HashMap<Object, Object>();
	protected Map<Object, Object> proxyProperties = new HashMap<Object, Object>();
	protected KrollAnnotationUtils utils;
	protected JSONUtils jsonUtils;
	protected String jsonPackage, jsonFile;

	protected boolean initialized = false;

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
	{
		if (!initialized) {
			initialize();
			initialized = true;
		}

		if (!roundEnv.processingOver()) {
			for (Element element : roundEnv.getRootElements()) {
				processKrollProxy(element);
			}

		} else {
			generateJSON();
		}

		return true;
	}

	protected void debug(String format, Object... args)
	{
		utils.debugLog(TAG, String.format(format, args));
	}

	protected void warn(String format, Object... args)
	{
		utils.debugLog(Diagnostic.Kind.WARNING, TAG, String.format(format, args));
	}

	protected void exception(Throwable t)
	{
		List<StackTraceElement> stackElements = Arrays.asList(t.getStackTrace());
		Collections.reverse(stackElements);
		for (StackTraceElement stackElement : stackElements) {
			utils.debugLog(Diagnostic.Kind.ERROR, TAG, "    " + stackElement.toString());
		}
		utils.debugLog(Diagnostic.Kind.ERROR, TAG, "Exception " + t.getClass() + " caught: " + t.getMessage());
	}

	protected void initialize()
	{
		utils = new KrollAnnotationUtils(processingEnv);
		jsonUtils = new JSONUtils(utils);

		debug("Running Kroll binding generator.");

		String jsonPackage = processingEnv.getOptions().get(PROPERTY_JSON_PACKAGE);
		this.jsonPackage = jsonPackage != null ? jsonPackage : DEFAULT_JSON_PACKAGE;

		String jsonFile = processingEnv.getOptions().get(PROPERTY_JSON_FILE);
		this.jsonFile = jsonFile != null ? jsonFile : DEFAULT_JSON_FILE;

		try {
			FileObject bindingsFile =
				processingEnv.getFiler().getResource(StandardLocation.SOURCE_OUTPUT, this.jsonPackage, this.jsonFile);

			// using the FileObject API fails to read the file, we'll use the pure file API
			String jsonPath = bindingsFile.toUri().toString();
			if (System.getProperty("os.name").contains("Windows")) {
				// the file URI in windows needs to be massaged (remove file:\)
				jsonPath = jsonPath.substring(6);
			}
			if (jsonPath.startsWith("file:/")) {
				jsonPath = jsonPath.substring(5);
			}

			properties = (Map<Object, Object>) JSONValue.parseWithException(new FileReader(jsonPath));
			debug("Succesfully loaded existing binding data: " + jsonPath);
		} catch (Exception e) {
			// file doesn't exist, we'll just create it later
			debug("No binding data found, creating new data file: %s/%s", this.jsonPackage, this.jsonFile);
		}
	}

	protected void processKrollProxy(final Element element)
	{
		utils.acceptAnnotations(
			element, new String[] { Kroll_proxy, Kroll_module }, new KrollVisitor<AnnotationMirror>() {
				protected Map<Object, Object> getProxyProperties(String packageName, String proxyClassName)
				{
					if (properties == null) {
						properties = new HashMap<Object, Object>();
					}
					return jsonUtils.getOrCreateMap(jsonUtils.getOrCreateMap(properties, "proxies"),
													packageName + "." + proxyClassName);
				}

				protected Map<Object, Object> getModule(String moduleClassName)
				{
					if (properties == null) {
						properties = new HashMap<Object, Object>();
					}
					return jsonUtils.getOrCreateMap(jsonUtils.getOrCreateMap(properties, "modules"), moduleClassName);
				}

				@Override
				public boolean visit(AnnotationMirror annotation, Object arg)
				{
					boolean isModule = utils.annotationTypeIs(annotation, Kroll_module);
					String packageName = utils.getPackage(element);
					String proxyClassName = utils.getName(element);
					String fullProxyClassName = String.format("%s.%s", packageName, proxyClassName);

					proxyProperties = getProxyProperties(packageName, proxyClassName);

					String genClassName = proxyClassName + "BindingGen";
					String sourceName = String.format("%s.%s", packageName, genClassName);
					String apiName = proxyClassName;
					int moduleIdx, proxyIdx;
					if ((proxyIdx = proxyClassName.indexOf("Proxy")) != -1) {
						apiName = proxyClassName.substring(0, proxyIdx);
					} else if ((moduleIdx = proxyClassName.indexOf("Module")) != -1) {
						apiName = proxyClassName.substring(0, moduleIdx);
					}

					HashMap<String, Object> proxyAttrs = utils.getAnnotationParams(annotation);
					if (proxyAttrs.get("name").equals(DEFAULT_NAME)) {
						proxyAttrs.put("name", apiName);
					} else {
						apiName = (String) proxyAttrs.get("name");
					}

					if (!proxyAttrs.containsKey("id") || proxyAttrs.get("id").equals(DEFAULT_NAME)) {
						proxyAttrs.put("id", fullProxyClassName);
					}

					debug("Found binding for %s %s", (isModule ? "module" : "proxy"), apiName);

					proxyAttrs.put("proxyClassName", String.format("%s.%s", packageName, proxyClassName));
					if (proxyAttrs.containsKey("creatableInModule")) {
						String createInModuleClass = (String) proxyAttrs.get("creatableInModule");
						if (!createInModuleClass.equals(Kroll_DEFAULT)) {
							jsonUtils.appendUniqueObject(getModule(createInModuleClass), "createProxies",
														 "proxyClassName", proxyAttrs);
						}
					}

					if (isModule && proxyAttrs.containsKey("parentModule")) {
						String parentModuleClass = (String) proxyAttrs.get("parentModule");
						if (!parentModuleClass.equals(Kroll_DEFAULT)) {
							jsonUtils.appendUniqueObject(getModule(parentModuleClass), "childModules", "proxyClassName",
														 proxyAttrs);
						} else {
							proxyAttrs.remove("parentModule");
						}
					}

					boolean isTopLevel = utils.hasAnnotation(element, Kroll_topLevel);
					proxyAttrs.put("isTopLevel", isTopLevel);
					if (isTopLevel) {
						HashMap<String, Object> topLevelParams = utils.getAnnotationParams(element, Kroll_topLevel);
						List<?> topLevelNames = (List<?>) topLevelParams.get("value");
						if (topLevelNames.size() == 1 && topLevelNames.get(0).equals(DEFAULT_NAME)) {
							topLevelNames = Arrays.asList(new String[] { apiName });
						}

						proxyAttrs.put("topLevelNames", topLevelNames);
					}

					BindingVisitor visitor = new BindingVisitor();
					final BindingVisitor fVisitor = visitor;
					if (utils.hasAnnotation(element, Kroll_dynamicApis)) {
						utils.acceptAnnotations(element, Kroll_dynamicApis, new KrollVisitor<AnnotationMirror>() {
							@Override
							public boolean visit(AnnotationMirror annotation, Object arg)
							{
								fVisitor.visitDynamicApis(annotation);
								return true;
							}
						});
					}

					TypeElement type = (TypeElement) element;
					Element superType = processingEnv.getTypeUtils().asElement(type.getSuperclass());

					String superTypeName = utils.getName(superType);
					if (!superTypeName.equals("Object")) {
						proxyProperties.put(
							"superProxyBindingClassName",
							String.format("%s.%sBindingGen", utils.getPackage(superType), superTypeName));
						proxyProperties.put("superPackageName", utils.getPackage(superType));
						proxyProperties.put("superProxyClassName", superTypeName);
					}

					proxyProperties.put("isModule", isModule);
					proxyProperties.put("packageName", packageName);
					proxyProperties.put("proxyClassName", proxyClassName);
					proxyProperties.put("genClassName", genClassName);
					proxyProperties.put("sourceName", sourceName);
					proxyProperties.put("proxyAttrs", proxyAttrs);

					if (isModule) {
						StringBuilder b = new StringBuilder();
						b.append(packageName).append(".").append(proxyClassName);
						Map<Object, Object> module = getModule(b.toString());
						module.put("apiName", apiName);
					}

					for (Element e : element.getEnclosedElements()) {
						e.accept(visitor, null);
					}

					return true;
				}
			});
	}

	protected class BindingVisitor
		extends SimpleElementVisitor6<Object, Object> implements KrollVisitor<AnnotationMirror>
	{
		@Override
		public String visitExecutable(ExecutableElement e, Object p)
		{
			utils.acceptAnnotations(e,
									new String[] { Kroll_method, Kroll_getProperty, Kroll_setProperty, Kroll_inject,
												   Kroll_topLevel, Kroll_onAppCreate, Kroll_interceptor },
									this, e);
			return null;
		}

		@Override
		public Object visitVariable(VariableElement e, Object p)
		{
			utils.acceptAnnotations(e, new String[] { Kroll_property, Kroll_constant, Kroll_inject }, this, e);
			return null;
		}

		public boolean visit(AnnotationMirror annotation, Object arg)
		{
			if (arg instanceof ExecutableElement) {
				ExecutableElement element = (ExecutableElement) arg;
				if (utils.annotationTypeIsOneOf(annotation, new String[] { Kroll_getProperty, Kroll_setProperty })) {

					visitDynamicProperty(annotation, element);
				} else if (utils.annotationTypeIs(annotation, Kroll_inject)) {
					visitInject(annotation, element, true);
				} else if (utils.annotationTypeIs(annotation, Kroll_topLevel)) {
					visitTopLevel(annotation, element);
				} else if (utils.annotationTypeIs(annotation, Kroll_dynamicApis)) {
					visitDynamicApis(annotation);
				} else if (utils.annotationTypeIs(annotation, Kroll_interceptor)) {
					visitInterceptor(annotation, element);
				} else if (utils.annotationTypeIs(annotation, Kroll_onAppCreate)) {
					visitOnAppCreate(annotation, element);
				} else {
					visitMethod(annotation, element);
				}
			} else if (arg instanceof VariableElement) {
				VariableElement element = (VariableElement) arg;
				if (utils.annotationTypeIs(annotation, Kroll_inject)) {
					visitInject(annotation, element, false);
				} else {
					visitProperty(annotation, element);
				}
			}
			return true;
		}

		protected void visitMethod(AnnotationMirror annotation, ExecutableElement element)
		{
			String methodName = element.getSimpleName().toString();

			Map<Object, Object> methods = jsonUtils.getOrCreateMap(proxyProperties, "methods");
			Map<Object, Object> methodAttrs = jsonUtils.getOrCreateMap(methods, methodName);
			List<Object> args = new ArrayList<Object>();
			jsonUtils.updateObjectFromAnnotation(methodAttrs, annotation);

			methodAttrs.put("hasInvocation", false);

			for (VariableElement var : element.getParameters()) {
				String paramType = utils.getType(var);
				if (paramType.equals(KrollInvocation)) {
					methodAttrs.put("hasInvocation", true);
				}

				String paramName = utils.getName(var);

				Map<Object, Object> argParams = new HashMap<Object, Object>();
				argParams.put("sourceName", paramName);
				argParams.put("type", paramType);
				jsonUtils.updateObjectFromAnnotationParams(argParams, utils.getAnnotationParams(var, Kroll_argument));

				String name = (String) argParams.get("name");
				if (name == null || name.equals(DEFAULT_NAME)) {
					argParams.put("name", paramName);
				}

				Object converter = argParams.get("converter");
				if (converter == null || Kroll_DEFAULT.equals(converter)) {
					argParams.put("converter", KrollConverter);
				}

				Object defaultValueProvider = argParams.get("defaultValueProvider");
				if (defaultValueProvider == null || Kroll_DEFAULT.equals(defaultValueProvider)) {
					argParams.put("defaultValueProvider", KrollConverter);
				}

				args.add(argParams);
			}

			Object converter = methodAttrs.get("converter");
			if (converter == null || Kroll_DEFAULT.equals(converter)) {
				methodAttrs.put("converter", KrollConverter);
			}

			Object defaultValueProvider = methodAttrs.get("defaultValueProvider");
			if (defaultValueProvider == null || Kroll_DEFAULT.equals(defaultValueProvider)) {
				methodAttrs.put("defaultValueProvider", KrollConverter);
			}

			methodAttrs.put("apiName", methodAttrs.get("name"));
			if (methodAttrs.get("name").equals(DEFAULT_NAME)) {
				methodAttrs.put("apiName", methodName);
			}

			methodAttrs.put("args", args);
			methodAttrs.put("returnType", element.getReturnType().toString());

			boolean isAsync = false;
			if (args.size() > 0) {
				Map<Object, Object> argParams = (Map<Object, Object>) args.get(args.size() - 1);
				if (argParams.get("type").equals(KrollPromise)) {
					isAsync = true;
				}
			}
			methodAttrs.put("isAsync", isAsync);
		}

		protected void visitProperty(AnnotationMirror annotation, VariableElement element)
		{
			boolean isConstant = utils.annotationTypeIs(annotation, Kroll_constant);

			Map<Object, Object> propertyMap =
				jsonUtils.getOrCreateMap(proxyProperties, isConstant ? "constants" : "properties");
			HashMap<String, Object> property = utils.getAnnotationParams(annotation);

			String type = utils.getType(element);
			property.put("type", type);
			String defaultName = utils.getName(element);
			property.put("proxyName", defaultName);

			String name = (String) property.get("name");
			if (name.equals(DEFAULT_NAME)) {
				property.put("name", defaultName);
				name = defaultName;
			}

			if (utils.annotationTypeIs(annotation, Kroll_property)) {
				Object nativeConverter = property.get("nativeConverter");
				if (nativeConverter == null || Kroll_DEFAULT.equals(nativeConverter)) {
					property.put("nativeConverter", KrollConverter);
				}

				Object javascriptConverter = property.get("javascriptConverter");
				if (javascriptConverter == null || Kroll_DEFAULT.equals(javascriptConverter)) {
					property.put("javascriptConverter", KrollConverter);
				}

			} else if (isConstant) {
				property.put("value", element.getConstantValue());
			}
			propertyMap.put(name, property);
		}

		protected void visitDynamicProperty(AnnotationMirror annotation, ExecutableElement element)
		{
			Map<Object, Object> dynamicProperties = jsonUtils.getOrCreateMap(proxyProperties, "dynamicProperties");
			HashMap<String, Object> params = utils.getAnnotationParams(annotation);
			Map<Object, Object> dynamicProperty = new HashMap<Object, Object>(params);

			String methodName = utils.getName(element);
			String defaultName = new String(methodName);
			if (defaultName.startsWith("get") || defaultName.startsWith("set")) {
				defaultName = Character.toLowerCase(defaultName.charAt(3)) + defaultName.substring(4);
			} else if (defaultName.startsWith("is")) {
				defaultName = Character.toLowerCase(defaultName.charAt(2)) + defaultName.substring(3);
			}

			String name = (String) dynamicProperty.get("name");
			if (name.equals(DEFAULT_NAME)) {
				dynamicProperty.put("name", defaultName);
				name = defaultName;
			}

			if (dynamicProperties.containsKey(name)) {
				dynamicProperty = (Map<Object, Object>) dynamicProperties.get(name);
			} else {
				// setup defaults
				dynamicProperty.put("get", false);
				dynamicProperty.put("set", false);
				dynamicProperty.put("nativeConverter", KrollConverter);
				dynamicProperty.put("javascriptConverter", KrollConverter);
				dynamicProperty.put("getHasInvocation", false);
				dynamicProperty.put("setHasInvocation", false);

				dynamicProperty.put("converter", KrollConverter);
				dynamicProperty.put("defaultValueProvider", KrollConverter);
			}

			ArrayList<Map<Object, Object>> args = new ArrayList<Map<Object, Object>>();
			for (VariableElement var : element.getParameters()) {
				String paramType = utils.getType(var);
				if (paramType.equals(KrollInvocation)) {
					if (utils.annotationTypeIs(annotation, Kroll_getProperty)) {
						dynamicProperty.put("getHasInvocation", true);
					} else {
						dynamicProperty.put("setHasInvocation", true);
					}
					continue;
				}

				String paramName = utils.getName(var);

				Map<Object, Object> argParams = new HashMap<Object, Object>();
				argParams.put("sourceName", paramName);
				argParams.put("type", paramType);
				jsonUtils.updateObjectFromAnnotationParams(argParams, utils.getAnnotationParams(var, Kroll_argument));

				String argName = (String) argParams.get("name");
				if (argName == null || argName.equals(DEFAULT_NAME)) {
					argParams.put("name", paramName);
				}

				Object converter = argParams.get("converter");
				if (converter == null || Kroll_DEFAULT.equals(converter)) {
					argParams.put("converter", KrollConverter);
				}
				Object defaultValueProvider = argParams.get("defaultValueProvider");
				if (defaultValueProvider == null || Kroll_DEFAULT.equals(defaultValueProvider)) {
					argParams.put("defaultValueProvider", KrollConverter);
				}
				args.add(argParams);
			}

			ArrayList<String> defaultProviders = new ArrayList<String>();
			for (VariableElement var : element.getParameters()) {
				if (utils.hasAnnotation(var, Kroll_argument)) {
					defaultProviders.add(
						(String) utils.getAnnotationParams(var, Kroll_argument).get("defaultValueProvider"));
				} else {
					defaultProviders.add(KrollConverter);
				}
			}

			if (utils.annotationTypeIs(annotation, Kroll_getProperty)) {
				dynamicProperty.put("get", true);
				dynamicProperty.put("getMethodName", methodName);
				dynamicProperty.put("getDefaultProviders", defaultProviders);
				dynamicProperty.put("getMethodArgs", args);
				dynamicProperty.put("getReturnType", element.getReturnType().toString());
			} else {
				dynamicProperty.put("set", true);
				dynamicProperty.put("setMethodName", methodName);
				dynamicProperty.put("setDefaultProviders", defaultProviders);
				dynamicProperty.put("retain", params.get("retain"));
				dynamicProperty.put("setMethodArgs", args);
				dynamicProperty.put("setReturnType", element.getReturnType().toString());
			}

			dynamicProperties.put(name, dynamicProperty);
		}

		protected void visitInject(AnnotationMirror annotation, Element element, boolean isMethod)
		{
			List<Object> injectList =
				jsonUtils.getOrCreateList(proxyProperties, isMethod ? "injectMethods" : "injectFields");
			HashMap<String, Object> attrs = utils.getAnnotationParams(annotation);

			String type = (String) attrs.get("type");
			String defaultType = null;
			if (isMethod) {
				List<? extends VariableElement> params = ((ExecutableElement) element).getParameters();
				if (params.size() > 0) {
					VariableElement firstParam = params.get(0);
					defaultType = utils.getType(firstParam);
				} else {
					warn("Skipping injection into method %s, at least one argument is required in a setter",
						 utils.getName(element));
					return;
				}
			} else {
				defaultType = utils.getType((VariableElement) element);
			}

			if (type.equals(Kroll_DEFAULT)) {
				attrs.put("type", defaultType);
			}

			String name = (String) attrs.get("name");
			if (name.equals(DEFAULT_NAME)) {
				attrs.put("name", utils.getName(element));
			}

			injectList.add(attrs);
		}

		protected void visitTopLevel(AnnotationMirror annotation, Element element)
		{
			Map<Object, Object> topLevelMethods = jsonUtils.getOrCreateMap(proxyProperties, "topLevelMethods");
			HashMap<String, Object> attrs = utils.getAnnotationParams(annotation);
			List<Object> topLevelNames = (List<Object>) attrs.get("value");
			if (topLevelNames.size() == 1 && topLevelNames.get(0).equals(DEFAULT_NAME)) {
				topLevelNames = Arrays.asList(new Object[] { utils.getName(element) });
			}

			topLevelMethods.put(utils.getName(element), topLevelNames);
		}

		protected void visitDynamicApis(AnnotationMirror annotation)
		{
			Map<Object, Object> dynamicApis = jsonUtils.getOrCreateMap(proxyProperties, "dynamicApis");
			HashMap<String, Object> attrs = utils.getAnnotationParams(annotation);
			List<Object> properties = (List<Object>) attrs.get("properties");
			List<Object> methods = (List<Object>) attrs.get("methods");
			for (Object p : properties) {
				if (!p.equals(DEFAULT_NAME)) {
					jsonUtils.getOrCreateList(dynamicApis, "properties").add(p);
				}
			}
			for (Object m : methods) {
				if (!m.equals(DEFAULT_NAME)) {
					jsonUtils.getOrCreateList(dynamicApis, "methods").add(m);
				}
			}
		}

		protected void visitInterceptor(AnnotationMirror annotation, ExecutableElement element)
		{
			// There should only be 1 of these per proxy
			Map<Object, Object> interceptor = jsonUtils.getOrCreateMap(proxyProperties, "interceptor");
			interceptor.put("name", utils.getName(element));
		}

		protected void visitOnAppCreate(AnnotationMirror annotation, Element element)
		{
			proxyProperties.put("onAppCreate", utils.getName(element));
		}
	}

	protected String getParentModuleClass(Map<String, Object> proxy)
	{
		String creatableInModule = (String) proxy.get("creatableInModule");
		String parentModule = (String) proxy.get("parentModule");

		if (creatableInModule != null && !creatableInModule.equals(Kroll_DEFAULT)) {
			return creatableInModule;

		} else if (parentModule != null && !parentModule.equals(Kroll_DEFAULT)) {
			return parentModule;
		}

		return null;
	}

	protected String findParentModuleName(Map<String, Object> proxy)
	{
		// Parent module name wasn't found because it exists in another source round (probably another module)
		// We can manually pull the annotation name here instead
		String parentModuleClass = getParentModuleClass(proxy);

		if (parentModuleClass != null) {
			TypeElement type = processingEnv.getElementUtils().getTypeElement(parentModuleClass);
			HashMap<String, Object> moduleParams = utils.getAnnotationParams(type, Kroll_module);

			String apiName = parentModuleClass.substring(parentModuleClass.lastIndexOf(".") + 1);
			int moduleIdx;
			if ((moduleIdx = apiName.indexOf("Module")) != -1) {
				apiName = apiName.substring(0, moduleIdx);
			}

			if (moduleParams.containsKey("name") && !moduleParams.get("name").equals(DEFAULT_NAME)) {
				apiName = (String) moduleParams.get("name");
			}
			return apiName;
		}

		return null;
	}

	protected void generateFullAPIName(Map<String, Object> proxyAttrs)
	{
		Map<String, Object> childProxyAttrs = proxyAttrs;
		String fullAPIName = (String) proxyAttrs.get("name");
		Map<String, Object> modules = (Map<String, Object>) properties.get("modules");
		Map<String, Object> proxies = (Map<String, Object>) properties.get("proxies");

		for (int i = 0; i < 10; i++) {
			if (childProxyAttrs == null) {
				break;
			}

			String name = (String) childProxyAttrs.get("name");
			if (name == null) {
				name = (String) childProxyAttrs.get("apiName");
			}

			String moduleClassName = getParentModuleClass(childProxyAttrs);
			String apiName = null;
			Map<String, Object> module = ((Map<String, Object>) modules.get(moduleClassName));
			if (module != null) {
				apiName = (String) module.get("apiName");
			}

			if (apiName == null && module != null) {
				apiName = findParentModuleName(module);
			}

			if (apiName == null) {
				break;
			}

			fullAPIName = apiName + "." + fullAPIName;

			Map<String, Object> proxyMap = (Map<String, Object>) proxies.get(moduleClassName);
			childProxyAttrs = (Map<String, Object>) proxyMap.get("proxyAttrs");
		}

		proxyAttrs.put("fullAPIName", fullAPIName);
	}

	protected void generateJSON()
	{
		try {
			Map<String, Object> proxies = jsonUtils.getStringMap(properties, "proxies");
			for (String proxyName : proxies.keySet()) {
				Map<String, Object> proxy = jsonUtils.getStringMap(proxies, proxyName);
				generateFullAPIName(jsonUtils.getStringMap(proxy, "proxyAttrs"));
			}

			FileObject file =
				processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, jsonPackage, jsonFile);
			debug("Generating JSON: %s", file.toUri());
			Writer writer = file.openWriter();

			writer.write(JSONValue.toJSONString(properties));
			writer.close();
		} catch (IOException e) {
			debug("Exception trying to generate JSON: %s/%s, %s", jsonPackage, jsonFile, e.getMessage());
		}
	}
}
