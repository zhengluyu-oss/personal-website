<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { knowledgeRequest as request, type KnowledgeEntry, type KnowledgePage } from '@/utils/knowledge-api'

type Endpoint = { kind: 'ENTRY' | 'ARTICLE'; id: string }
type RelationType = 'EXPLAINS' | 'DEPENDS_ON' | 'REFERENCES' | 'RELATED_TO'
type Relation = { id: string; revision: number; from: Endpoint; to: Endpoint; type: RelationType; note: string }
type GraphNode = { endpoint: Endpoint; title: string; kind: string; url: string }
type Graph = { nodes: GraphNode[]; edges: Relation[]; truncated: boolean; hasMore: boolean }
type Neighbor = { relation: Relation; other: GraphNode }
const props = defineProps<{ entry: KnowledgeEntry }>()
const emit = defineEmits<{ open: [id: string] }>()
const labels: Record<RelationType, string> = { EXPLAINS: '解释', DEPENDS_ON: '依赖', REFERENCES: '参考', RELATED_TO: '相关' }
const root = computed<Endpoint>(() => ({ kind: 'ENTRY', id: props.entry.id }))
const graph = ref<Graph>(), neighbors = ref<Neighbor[]>([]), page = ref(1), total = ref(0)
const depth = ref(1), showGraph = ref(false), busy = ref(false), error = ref('')
const graphElement = ref<HTMLDivElement>(), graphInstance = ref<{ dispose: () => void; resize: () => void }>()
let sequence = 0
let disposed = false

function nodeKey(endpoint: Endpoint) { return endpoint.kind + ':' + endpoint.id }
async function load() {
  const current = ++sequence; busy.value = true; error.value = ''
  try {
    const [local, adjacent] = await Promise.all([
      request<Graph>('POST', `/view/relations/graph?depth=${depth.value}`, root.value),
      request<KnowledgePage<Neighbor>>('POST', `/view/relations/neighbors?page=${page.value}&size=20`, root.value),
    ])
    if (disposed || current !== sequence) return
    graph.value = local; neighbors.value = adjacent.records; total.value = adjacent.total
    if (showGraph.value) await draw()
  } catch (failure: any) { if (current === sequence) error.value = failure.message }
  finally { if (current === sequence) busy.value = false }
}
async function draw() {
  if (!showGraph.value || !graph.value) return
  await nextTick()
  if (!graphElement.value) return
  const echarts = await import('echarts')
  if (disposed || !graphElement.value || !showGraph.value) return
  graphInstance.value?.dispose()
  const chart = echarts.init(graphElement.value)
  graphInstance.value = chart
  chart.setOption({
    animation: false,
    legend: [{ data: ['笔记', '资料', '概念', '文章'] }],
    series: [{ type: 'graph', layout: 'force', roam: true, draggable: false,
      force: { repulsion: 230, edgeLength: 130 }, label: { show: true, position: 'bottom', formatter: '{b}' },
      categories: [{ name: '笔记' }, { name: '资料' }, { name: '概念' }, { name: '文章' }],
      data: graph.value.nodes.map(node => ({ id: nodeKey(node.endpoint), name: node.title,
        category: ({ NOTE: 0, DOCUMENT: 1, CONCEPT: 2, ARTICLE: 3 } as Record<string, number>)[node.kind] ?? 0,
        symbolSize: node.endpoint.id === props.entry.id ? 52 : 34 })),
      links: graph.value.edges.map(edge => ({ source: nodeKey(edge.from), target: nodeKey(edge.to),
        value: labels[edge.type], lineStyle: { curveness: edge.type === 'RELATED_TO' ? .1 : 0 } })),
      edgeSymbol: ['', 'arrow'], edgeSymbolSize: [0, 7], emphasis: { focus: 'adjacency' } }],
  })
  chart.on('click', (event: any) => {
    const selected = graph.value?.nodes.find(node => nodeKey(node.endpoint) === event.data?.id)
    if (selected) navigate(selected)
  })
}
async function toggleGraph() {
  showGraph.value = !showGraph.value
  if (showGraph.value) await draw()
  else { graphInstance.value?.dispose(); graphInstance.value = undefined }
}
function navigate(node: GraphNode) {
  if (node.endpoint.kind === 'ENTRY') emit('open', node.endpoint.id)
  else window.open(node.url, '_blank', 'noopener,noreferrer')
}
watch(() => props.entry.id, () => { page.value = 1; graph.value = undefined; neighbors.value = []; void load() }, { immediate: true })
const resize = () => graphInstance.value?.resize()
onMounted(() => window.addEventListener('resize', resize))
onBeforeUnmount(() => { disposed = true; sequence++; graphInstance.value?.dispose(); window.removeEventListener('resize', resize) })
</script>

<template>
  <section class="connections" aria-label="知识关联">
    <header><div><h3>知识关联</h3><p>由站主手动建立；列表与关系图展示相同的可见资料。</p></div><button @click="load">刷新关联</button></header>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="toolbar"><button @click="toggleGraph">{{ showGraph ? '收起关系图' : '查看关系图' }}</button><label>展开层数<select v-model.number="depth" @change="load"><option :value="1">一跳</option><option :value="2">两跳</option></select></label></div>
    <div v-if="showGraph" ref="graphElement" class="graph" role="img" aria-label="知识关系图。下方列表提供等价的键盘可访问内容。"></div>
    <p v-if="graph?.truncated" role="status">图谱已达到展示上限，可翻页查看下方相邻关系列表。</p>
    <p v-if="busy">读取关联中…</p><p v-else-if="!total">暂无关联。</p>
    <ul v-else><li v-for="item in neighbors" :key="item.relation.id">
      <button class="node" @click="navigate(item.other)"><span>{{ item.other.title }}</span><small>{{ item.other.kind === 'ARTICLE' ? '博客文章' : '知识条目' }}</small></button>
      <span>{{ item.relation.type === 'RELATED_TO' ? '↔' : item.relation.from.kind === 'ENTRY' && item.relation.from.id === entry.id ? '→' : '←' }} {{ labels[item.relation.type] }}</span>
      <p v-if="item.relation.note">{{ item.relation.note }}</p>
    </li></ul>
    <nav v-if="total > 20"><button :disabled="page <= 1" @click="page--; load()">上一页</button><span>第 {{ page }} 页 · 共 {{ total }} 条</span><button :disabled="page * 20 >= total" @click="page++; load()">下一页</button></nav>
  </section>
</template>

<style scoped>
.connections{margin-top:30px;padding-top:20px;border-top:1px solid #dfe5ed}.connections header,.toolbar,.relation-form,nav{display:flex;gap:12px;align-items:end;flex-wrap:wrap}.connections header{justify-content:space-between}.connections h3{margin:0;font-size:20px}.connections p{line-height:1.6}.relation-form{margin:18px 0}.relation-form label,.toolbar label{display:grid;gap:6px;min-width:130px}.relation-form label:nth-child(2){flex:1}.connections input,.connections select,.connections button{font:inherit;border:1px solid #cdd6e2;border-radius:6px;padding:8px;background:transparent;color:inherit}.connections button{cursor:pointer}.connections button:disabled{opacity:.5}.connections ul{padding:0;list-style:none}.connections li{border-top:1px solid #e1e6ed;padding:12px 0;display:flex;gap:12px;align-items:center;flex-wrap:wrap}.connections li p{width:100%;margin:0}.node{display:grid;text-align:left;min-width:160px}.node small{color:#64748b}.graph{height:380px;width:100%;min-width:0;margin:16px 0}nav{justify-content:space-between}@media(max-width:600px){.graph{height:280px}.relation-form label{width:100%}.connections li{align-items:flex-start}}
</style>
