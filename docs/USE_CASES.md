# penTex use cases and requirements

A student can take one piece of handwriting through several workflows. For example, writing `2+3=` starts recognition (UC1). Clicking **Calculate** evaluates the recognized formula (UC2), places the answer beside the ink (UC3), and publishes the result to the material library and History (UC5a and UC6). Saving the canvas (UC7) preserves the board layout. Exporting a PDF (UC5) produces a separate document.

## Requirements and evidence

The supplied four-page CSC207 *Project README and Minimum Viable Product (MVP)* PDF sets course deliverables. Page 1 asks for a project domain, objective and team user stories. Page 2 asks for use cases grounded in those stories, a declared MVP, implementation leads and entities with state and behaviour. Pages 2–3 discuss design diagrams. Page 3 requires a Java API call, documentation and a statement of what actually worked. Page 4 describes MVP demonstration expectations, including the case where the API is incomplete. The supplied Markdown template organizes these deliverables. Neither attachment specifies penTex's product features, a quantitative recognition accuracy or latency target, or individual implementation ownership.

The team's historical README supplies the product baseline, including its story numbering, named leads and MVP decisions. The parent of the audited revision contains this document. Earlier planning mentioned Desmos and photo input. The shipped board instead computes graphs locally and provides handwriting input on its canvas. The labels in the table refer to implemented paths from the production composition root at `bb8806d`. Runtime test results and platform coverage are in the project README. Acceptance examples here describe expected behavior in those paths. [Historical team specification](https://github.com/ZzzymFr/penTex/blob/09d6004/README.md) · [Earlier planning](https://github.com/ZzzymFr/penTex/blob/329e926/README.md) · [Composition root](../src/main/java/app/StackedAppBuilder.java) · [Verification](../README.md)

| Story and student objective | Historical scope and lead | What the code supports |
| --- | --- | --- |
| US1 — Enter mathematical notation by hand or from a photo | UC1, MVP, Yuanmo Zha | **Partial story.** Canvas recognition is implemented. Uploading a photograph for recognition was planned and has no board entry point. |
| US2 — Obtain an answer for written mathematics | UC2, MVP, Haoxiang Zhao | **Implemented** for supported expressions, definitions and one-unknown conditions. Standalone integrals take the separate UC2a service path. |
| US3 — Read the answer next to the original work | UC3, MVP, Haoxiang Zhao | **Implemented.** New answers use typeset math. A saved answer retains its association with the formula. |
| US4 — Explore a function visually | UC4 and UC4b, MVP, Haoyu Wang | **Implemented.** Multiple independent graph and portrait cards, cursor traces and coordinate lookups are available. |
| US5 — Assemble legible mathematical notes and export a PDF | UC5, MVP, Bojun (Eric) Yang | **Implemented.** The PDF contains rendered pages and adds copyable source for eligible formula materials. UC5a covers gathering and revising those materials. |
| US6 — Retrieve and reuse work across sessions | UC6 and UC7, beyond MVP, Bojun (Eric) Yang | **Implemented** for the local History archive and named canvas saves. The editable PDF document and calculator function dictionary have session lifetimes. |

UC2a and UC5a are subdivisions introduced here to make the API and material flows explicit. For UC1 through UC7, the **primary actor is the student** and the common precondition is a running Math Lab board. The historical leads above describe the team's assignment. The contributor notes below describe Git evidence for actual work. Course presentation, TA approval, team meeting attendance, conceptual ownership, recognition accuracy on arbitrary handwriting and identical desktop behavior on both supported operating systems cannot be verified from the implementation. [Contribution evidence](CONTRIBUTIONS.md) · [Repository platform constraints](../AGENTS.md)

### Course requirements mapped to evidence

| PDF reference | Evidence at the audited revision | Assessment |
| --- | --- | --- |
| p. 1 · Name, domain and student stories | The team identified penTex as an education project and described six student goals. The signed team contract lists four members. | Documented. The brief suggests one story per member as a planning aim. |
| p. 2 · Derive use cases and identify the MVP | The team's historical specification assigns UC1–5, including UC4b, to the MVP. UC6 and UC7 were added later. Their current implementations are reachable from the same board. | MVP scope is documented. Each case below identifies its trigger, outcome, alternative paths and implementation evidence. |
| p. 2 · Each member leads an end-to-end use case | All four have a recorded MVP assignment. Evaluation and graphing have named interactors, controllers and presenters. Recognition crosses a shared canvas worker, a recognizer interface and a model adapter. PDF editing combines a controller, editor, export interactor and Swing components. The Git map traces those pieces to several authors. | Assignments and specific patches are verifiable. The rubric's expectation that a lead code most of each layer cannot be established uniformly from Git. |
| pp. 2–3 · At least two domain entities with state and behavior | The README describes the stored fields and operations of Stroke, Context and PdfDocument. The source inventory covers their callers, dependencies and tests. | Implemented and documented. |
| pp. 3–4 · Call an API from Java, cite its documentation and report success | UC2a traces an HTTPS request containing the encoded integrand to Newton. The team previously described a response. Gateway tests supply controlled responses. The live Java attempt in this environment failed to establish a proxy tunnel (HTTP 403). | Integration implemented. Current live success unverified. No service call is needed for ordinary local evaluation or plots. |
| pp. 2–4 · Suggested diagrams, UI sketches and an MVP demonstration | The README includes architecture and calculation sequence diagrams. Tests and the Linux desktop smoke check exercise the application. | Diagrams provided. The materials supply no evidence of a particular course presentation, assessment or cross-platform demo. |

The PDF gives no numerical performance, accuracy or reliability thresholds. The repository adds Java 17, Maven, Swing, and Windows/macOS interaction and layout requirements. Linux test results cover a different platform. [Course brief](https://q.utoronto.ca/courses/444184/pages/project-readme-and-minimum-viable-product-mvp) · [Entity descriptions and verification](../README.md) · [Layer-by-layer contribution evidence](CONTRIBUTIONS.md) · [Platform requirements](../AGENTS.md)

## UC1 — Recognize handwritten mathematics

**Objective and preconditions.** Turn ink into a visible LaTeX interpretation before acting on it. The model must have initialized and the canvas must contain strokes. This covers the canvas-input portion of US1.

1. Write a formula, such as `2+3=`, and lift the pen. `DrawingCanvas` records world-space points in strokes. A 500 ms pause timer in `AnswerCanvasPage` groups loose ink or captures an edited formula. A lasso or rectangle instead identifies the formula directly.
2. `CaptureReader.read` passes the capture to the recognizer through `SharedCanvas`. If trailing equals ink is detected but the prediction lost that sign, a second read can recover it. `VitalMiniFormulaRecognizer.recognize` applies `FormulaImagePreprocessor.prepare` and calls `vital_mini.generate` with beam width 3.
3. `AnswerWorker` returns the prediction on Swing's event thread. The page checks the formula revision and user-action generation before displaying a popup. A preview alone does not save the result.

**Variations.** A completed relation can trigger the popup without a final equals sign. A finished calculable expression can receive a faint equals chip. A selection offers Calculate, Graph, Recognize and Phase Portrait, enabling actions according to the expression and whether it has an answer. A standalone definition normally offers **Recognize**. The pen, width and color controls, precise or whole-stroke eraser, selection transforms, canvas undo and redo provide ways to correct the input. A blank or failed read supplies no usable formula. An unreadable eligible capture can produce a notice. Selecting ink again can recover a missed automatic popup.

**Evidence and contributors.** Frank/Frankie implemented the model path, Haoxiang Zhao connected canvas capture, pause grouping and the recognizer, RAY/Mengwanzhouoffical contributed image preparation, and Eric contributed pen and editing controls. The adapter test injects a reader. Its assertions check preparation and forwarding. A separate Linux run through the production recognition adapter returned predictions for all 29 images in the checked-in validation manifest and matched 16 labels exactly. It read `5+3=` and `y=ax+b` correctly, but interpreted the leading 8 in `8x+4=20` as `\delta`. [Canvas and popup](../src/main/java/view/canvas/AnswerCanvasPage.java) · [Recognition adapter](../src/main/java/data_access/VitalMiniFormulaRecognizer.java) · [Adapter test](../src/test/java/data_access/VitalMiniFormulaRecognizerTest.java)

## UC2 — Calculate a formula or define a reusable function

**Objective and preconditions.** Calculate from a recognized expression, with the relevant action enabled. This implements US2 for the parser's supported grammar.

1. Choose **Calculate** for `2+3=`. The worker reuses the recognized text. `EvaluateController.execute` passes an input object to `IntegralRouter.execute`, which routes ordinary expressions to `EvaluateInteractor.execute`.
2. The interactor tokenizes with `MathLexer`, strips a trailing answer prompt where appropriate, and parses with `MathParser` using the session's function dictionary. A closed expression is evaluated in a `Context`. A single free variable in a condition invokes `ConditionAnswerer`. A supported symbolic derivative invokes `SymbolicDerivative`.
3. The output boundary receives values, solutions, intervals or a symbolic expression. `EvaluatePresenter` creates the presentation state consumed by UC3. For the example, the answer is 5.

**Variations.** Selecting **Recognize** on a definition such as `f(x)=x^2` invokes the calculation path. `FunctionDefiner.define` validates the definition and saves it through the in-memory DAO. Later expressions such as `f(3)=` can use it in that application session. The board publishes the definition to History, which stores text independently of the active dictionary. Closed expressions include supported arithmetic, calculus, set, vector and logical operations. Evaluating multiple ± signs enumerates at most four signs and deduplicates equal answers. Free variables without a solvable condition, malformed input and unsupported expressions produce differentiated failure messages. Numerical root searches and finite work budgets limit the problems this implementation can solve.

**Evidence and contributors.** Haoxiang Zhao implemented the local evaluation engine, function definitions, presenter and popup action checks. RAY supplied integral routing and uses the local evaluator for finite bounds in UC2a. [Evaluation dispatch](../src/main/java/use_case/evaluate/EvaluateInteractor.java) · [Definitions](../src/main/java/use_case/evaluate/FunctionDefiner.java) · [Evaluation tests](../src/test/java/use_case/evaluate/EvaluateInteractorTest.java)

### UC2a — Integrate a standalone expression

**Objective and preconditions.** Obtain an antiderivative or a definite value. The recognized expression must match a standalone integral with a differential. A successful service call requires network access to Newton. This is US2's external-service path and the integration relevant to the PDF's Java API requirement.

1. **Calculate** passes the integral through `IntegralRouter` to `IntegrateInteractor.execute`. For definite integrals it validates the bounds before requesting the antiderivative.
2. `NewtonAntiderivativeGateway.find` translates the integrand, encodes it as a URL path segment, and uses `JdkNewtonHttp.get`. It reads the JSON string field `result` and translates the response back to the original variable.
3. An indefinite answer displays the antiderivative plus the integration constant. For finite bounds, `DefiniteIntegral.value` checks sampled values and computes the endpoint difference locally. `IntegratePresenter` publishes to the same view model as UC2.

**Variations and status.** A timeout or unavailable endpoint, unusable remote response, improper bounds or detected undefined interval produce a failure notice. Sampling may miss a singularity. A larger expression containing an integral is outside this service route. The Java request and response handling are implemented. Tests that supply a gateway establish local behavior with known answers. Current live-service verification is reported separately in the README. RAY implemented the gateway, routing and integral use case. [Newton gateway](../src/main/java/data_access/newton/NewtonAntiderivativeGateway.java) · [Integration interactor](../src/main/java/use_case/integrate/IntegrateInteractor.java) · [Tests](../src/test/java/use_case/integrate/IntegrateInteractorTest.java)

## UC3 — Read the calculated answer beside the ink

**Objective and preconditions.** Preserve the visual connection between an answer and its source for US3. A calculation must complete against a current formula revision.

1. `EvaluatePresenter` or `IntegratePresenter` publishes result text, display LaTeX and placement information to `MathLabViewModel`. `AnswerWorker.viewModelChanged` receives the result and posts delivery to the event thread.
2. `AnswerCanvasPage.deliver` calls `DrawingCanvas.showTypesetAnswer` through the shared canvas. `AnswerPlacer.placeTypeset` uses stroke height and pen width to size the result. Relations can receive an answer below their source. When this would hit other ink, placement falls back to the right.
3. A successful placement attaches a `CanvasAnswer` to the formula and allows publication for that revision. Moving the formula keeps the answer with it. Changing its ink invalidates the old answer. UC7 serializes typeset answer source as well as the display geometry.

**Variations.** A definition produces a confirmation card. Invalid or unsupported input displays an error and can publish the formula without a calculated result. A symbolic derivative over the presenter's 60-character plain-text limit takes the error path. A revision check prevents an old answer worker result from overwriting newly edited ink. JLaTeXMath lays out the answer. When the bundled Kalam font is available, `TexAnswer` registers it for Basic Latin glyphs while parsing canvas answers. Other math glyphs retain TeX rendering.

**Evidence and contributors.** Haoxiang Zhao implemented formatting, placement, stale-answer handling and the typeset answer path, including LaTeX persistence. Eric implemented the workspace snapshot and the PDF/History bridge used by this flow. [Presenter](../src/main/java/interface_adapter/evaluate/EvaluatePresenter.java) · [Placement](../src/main/java/view/answer/AnswerPlacer.java) · [Typesetting](../src/main/java/view/answer/TexAnswer.java) · [Typeset answer tests](../src/test/java/view/DrawingCanvasTypesetAnswerTest.java)

## UC4 — Plot and explore curves and surfaces

**Objective and preconditions.** Visualize a graphable expression from US4, for example `y=x^2`. Select the formula when its automatic popup offers another action.

1. Choose **Graph**. `PopupGraphing` obtains a session. `GraphFunctionInteractor.execute` validates the ranges and attempts explicit curve, explicit surface and implicit equation parsing. Curves are sampled over a viewport. Implicit 2D equations use contours and implicit 3D equations use a surface mesh.
2. The interactor saves the plot in its session and publishes data through `GraphPresenter`. A live card appears on the board. A creation event also renders a graph snapshot for the Library and History.
3. Pan or zoom, change axis ranges, trace a point, or use coordinate lookup. `ExploreGraphInteractor` operates on that card's current plot. The 3D view can rotate. Cards can be moved, resized or closed individually. Exploration updates a view without recording each cursor movement in History.

**Variations.** Drag a lassoed compatible formula onto a graph to add a layer. Explicit curves can share a card with other explicit curves, and explicit surfaces with surfaces. Mixing the two dimensions or overlaying implicit equations is rejected. Empty input, invalid ranges, or sampling that finds no drawable points yields a reason. More than one graph or portrait can remain live. Historical references to QuickChart describe an earlier implementation.

**Evidence and contributors.** RAY/Mengwanzhouoffical implemented graphing, the transition to live local rendering, exploration and multi-function cards. Haoxiang Zhao contributed the implicit 3D reader and mesh plot path. [Graph dispatch](../src/main/java/use_case/graph_function/GraphFunctionInteractor.java) · [Exploration](../src/main/java/use_case/explore_graph/ExploreGraphInteractor.java) · [Multiple-card tests](../src/test/java/view/canvas/PopupMultipleGraphsTest.java)

### UC4b — Explore a differential equation

**Objective and preconditions.** Visualize a supported differential equation from US4. The recognized expression must describe a supported first-order slope field, autonomous second-order equation, or two-equation planar system.

1. Write or select an equation such as `y'=y`. Choose **Phase Portrait**. The automatic popup can show this action alone. A lassoed selection retains its four action slots.
2. `PhasePortraitInteractor.execute` parses with `DifferentialEquationParser`, validates the viewport and constructs a `PhasePlot` with sampled directions and equilibrium information. The presenter supplies the card and the builder publishes a snapshot for reuse.
3. Hover to trace a numerical solution through a point, click to toggle a pinned solution, or use the lookup and range controls. `ExplorePhaseInteractor.toggleSolution` updates that portrait's session. The card retains up to eight pinned paths.

**Variations.** Unsupported derivatives or variables, a non-increasing axis range, or a field undefined throughout the sampled window yield an error. Reset clears pinned solutions. A second graph function cannot be dropped into a phase portrait. A portrait keeps its own session alongside other cards, superseding the shared-card description in the historical README.

**Evidence and contributors.** RAY implemented the phase parser, field and trajectory calculations, session, presenters and controls. The parser shares underlying expression machinery implemented by Haoxiang Zhao. [Creation](../src/main/java/use_case/phase_portrait/PhasePortraitInteractor.java) · [Exploration](../src/main/java/use_case/explore_phase/ExplorePhaseInteractor.java) · [Popup tests](../src/test/java/view/canvas/PopupPhasePortraitTest.java)

## UC5 — Compose, preview and export a PDF

**Objective and preconditions.** Build a paginated set of notes for US5. Open the **PDF** panel. The Library, History or canvas can supply materials, or the student can begin with text. Export requires a writable destination.

1. Add material to the current A4 page and edit the document using the ribbon and page canvas. The controller sends commands to `DocumentEditor`, whose document contains placements and styled body text per page. Drag and resize items, layer pictures relative to text, split a calculation, add notes, reflow text, or add pages.
2. **Preview** calls `PdfExportInteractor.preview`. A copied document goes to `PdfBoxOutputAdapter`, which builds the PDF and rasterizes it into preview pages at 90 DPI.
3. **Export PDF** opens a file chooser. Confirm replacement if the output already exists. `PdfExportInteractor.export` sends a document snapshot to `PdfExporter`. It draws layers in order and appends copyable formula/result source pages for placed text-based math materials. Main-page artwork and body text are rendered as images.

**Variations.** Undo and redo operate on the editable document. Auto layout appends the current Library items below existing placements and adds pages as needed. Applying it twice can place the same material twice. Group transforms move or resize existing selections. At least one page must remain. Canceling the chooser returns to the editor. Preview or export I/O failures produce an error. Export produces a PDF file. The examined board provides no editable PDF project save/open command. The canvas-save command belongs to UC7.

**Evidence and contributors.** Eric (Eric-Yang-33213/Phantom) implemented the document editor, layout and PDFBox export, including integration of History and canvas material. Graph and calculation events supplied by RAY and Haoxiang Zhao feed this workflow. [Document operations](../src/main/java/pdf_editor/use_case/DocumentEditor.java) · [PDF writer](../src/main/java/pdf_editor/data_access/PdfExporter.java) · [Adapter integration test](../src/test/java/pdf_editor/data_access/PdfBoxOutputAdapterIntegrationTest.java)

### UC5a — Collect and revise reusable materials

**Objective and preconditions.** Bring content from the board or History into US5 and correct its LaTeX without rewriting it. Collection needs source ink, an existing record, or a computed result. Revision needs an editable formula material.

1. After calculation or graph creation, `MathLabCollector` accepts structured formula/result or graph PNG data. The student can also use the selection context menu to recognize selected ink into the Library or keep its appearance as artwork. **Capture area** takes a rectangular board image. History and canvas drags supply additional inputs.
2. Place the material on a page or edit an eligible formula. The formula-edit UI can reuse the shared calculator through `HistoryFormulaCalculator`. `FormulaRevisionCoordinator` coordinates the new content with matching Library material, PDF placements and durable History.
3. The document refreshes matching placements. History records the revised content and retires the previous entry only when identified uniquely. Editing a PDF placement checks that the selection is still current.

**Variations.** An empty recognition or removal of the original strokes while recognition runs can prevent insertion. Changing the selection alone leaves those strokes eligible. Graphs and ink images retain their pixels. Erasing the source ink preserves material explicitly collected into the Library. The library and page arrangement live in memory. Library clearing, History clearing and canvas erasing act on different stores.

**Evidence and contributors.** Eric implemented the library, selected-ink capture, drag/drop, revision and History coordination. These adapters reuse the model and calculation paths from UC1 and UC2. [Selection menu](../src/main/java/pdf_editor/view/CanvasLibrarySelectionMenu.java) · [Revision coordinator](../src/main/java/pdf_editor/adapter/FormulaRevisionCoordinator.java) · [Revision tests](../src/test/java/pdf_editor/adapter/FormulaRevisionCoordinatorTest.java)

## UC6 — Browse and reuse local History

**Objective and preconditions.** Recover previous work and reuse it on the board or in a PDF, implementing US6 after the historical MVP. Recording requires a writable user History directory. Browsing existing records requires read access.

1. A confirmed recognition, calculation or graph event reaches `HistoryInteractor` through the collector. `FileHistoryRepository` saves local entries. The content key includes kind, trimmed formula/result and, for graphs, dimensions and PNG bytes. Recording a calculation upgrades matching formula-only entries.
2. Open **History**. `HistoryBoardPresenter.refresh` reads entries newest first, filters legacy punctuation-only formulas and updates `HistoryViewModel`. Bare recognition previews do not create permanent entries.
3. Drag a row onto the canvas or into the PDF workspace. `BoardWindow.droppedAt` routes by destination. The board receives placed artwork with corresponding metadata, or the PDF editor receives a reusable math or graph material.

**Variations.** Single-record deletion and confirmed **Clear All** are exposed in the board. The interactor and presenter contain `undoDelete`, but the production History panel connects deletion and clearing without a corresponding undo action. Damaged history records can be skipped. Erasing the source strokes leaves the saved archive intact. The optional `CanvasHistoryLifecycle` can suspend linked records in isolation, while the production collector is wired to the archive-preserving path. These behaviors differ from the historical description of undo-delete and automatically hiding entries.

**Evidence and contributors.** Eric implemented durable entries, board presentation, drag/drop and archive-retention behavior. Haoxiang Zhao supplied confirmed result publication and RAY supplied graph snapshots. [History interactor](../src/main/java/history/use_case/HistoryInteractor.java) · [Board routing](../src/main/java/view/board/BoardWindow.java) · [Board integration tests](../src/test/java/history/adapter/OneBoardHistoryIntegrationTest.java) · [Erasure retention test](../src/test/java/pdf_editor/CanvasErasureArchiveRetentionTest.java)

## UC7 — Save and reopen a named canvas

**Objective and preconditions.** Resume a student's editable board under US6. Saving requires write access to the `.mathlab/canvases` directory under the user's home.

1. Choose **Save** or the platform save shortcut. The first save asks for a name. `CanvasWorkspaceDialogs` obtains an immutable `CanvasState` from `DrawingCanvas.exportCanvasState`. It includes stroke samples and styles, formula membership, answers, placed images, graph snapshots and viewport.
2. `CanvasWorkspace.save` selects a canvas ID and writes through `FileCanvasRepository.write` to a temporary file, replacing the target using an atomic move where supported. Save As creates a separate named canvas.
3. Choose **Open** and select a save. `DrawingCanvas.restoreCanvasState` validates references and assembles the new data before replacing the board. Startup tries to restore the most recently modified save. Saved graph pictures preserve their appearance. The binary format has no live interaction session, active calculator function dictionary, PDF pages or material library.

**Variations.** The chooser offers new, rename and delete operations. It confirms permanent deletion. Opening another canvas, starting a new canvas or closing the board with unsaved canvas content offers save, discard or cancel. An unchanged current save avoids a rewrite. Blank names are rejected, malformed entries can be skipped during listing, and earlier canvas formats receive defaults when fields are absent.

**Evidence and contributors.** Eric implemented the named workspace, repository and board commands. Haoxiang Zhao added typeset answer source to the v4 file format and supported earlier formats. [Workspace service](../src/main/java/workspace/use_case/CanvasWorkspace.java) · [Binary storage](../src/main/java/workspace/data_access/FileCanvasRepository.java) · [Canvas round-trip tests](../src/test/java/view/CanvasWorkspaceRoundTripTest.java) · [LaTeX compatibility tests](../src/test/java/workspace/FileCanvasRepositoryLatexTest.java)

## Developer workflow — Evaluate and export the handwriting model

**Actor, objective and status.** A model developer can prepare or assess a recognition checkpoint for UC1 outside the student application. This supporting workflow is implemented in the Python tools. The course PDF leaves the training method and dataset to the team. A full training run, its compute cost and independently reproduced accuracy are outside the runtime checks reported here.

1. Provide the chosen training or evaluation data, matching vocabulary, Python dependencies and output locations. The dataset notes describe source splits and preparation. Training and Java runtime files use different default locations. A developer must select the intended weights explicitly when they are outside a script's default path.
2. Train with the common training entry point or select the decoder-training variant, which freezes the CNN. To score an existing checkpoint, the generation evaluator accepts a validation/test split and beam width. The separate image-directory tool can run a checkpoint on images already on disk.
3. The exporter reads a training checkpoint and produces TorchScript operations and a JSON manifest. Java loads the operations checkpoint and tokenizer for desktop inference. The exporter rejects identical checkpoint and output paths. Its existence does not establish the training provenance of every image or a photo-upload feature on the student board.

**Contributors and evidence.** Frank/Frankie contributed the Python model, export and Java inference path. Dataset suppliers are credited in the dataset document. [Dataset notes](../src/neutral%20network/DATASETS.md) · [Training variant](../src/neutral%20network/train_decoder.py) · [Checkpoint evaluation](../src/neutral%20network/evaluate_generation.py) · [Exporter](../src/neutral%20network/export_torchscript_ops.py)
