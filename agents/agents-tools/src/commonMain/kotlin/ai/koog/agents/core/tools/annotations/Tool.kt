package ai.koog.agents.core.tools.annotations

/**
 * Annotation for tools to be collected by reflection from an object.
 *
 * @property customName The custom name of the tool. If not provided, the name of the function is used.
 */
@Target(AnnotationTarget.FUNCTION)
@Suppress("unused")
public annotation class Tool(val customName: String = "")
