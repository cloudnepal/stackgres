<template>
	<div id="pg-config" v-if="iCanLoad">
		<div class="content">
			<template v-if="!$route.params.hasOwnProperty('name')">
				<table id="postgres" class="configurations pgConfig resizable fullWidth" v-columns-resizable>
					<thead class="sort">
						<th class="sorted desc name hasTooltip">
							<span @click="sort('data.metadata.name')" title="Name">
								Name
							</span>
							<span class="helpTooltip" :data-tooltip="getTooltip('sgpostgresconfig.metadata.name')"></span>
						</th>
						<th class="desc postgresVersion hasTooltip" data-type="version">
							<span @click="sort('data.spec.postgresVersion')" title="Postgres Version">
								PG
							</span>
							<span class="helpTooltip" :data-tooltip="getTooltip('sgpostgresconfig.spec.postgresVersion')"></span>
						</th>
						<th class="config notSortable hasTooltip">
							<span title="Parameters">
								Parameters
							</span>
							<span class="helpTooltip" :data-tooltip="getTooltip('sgpostgresconfig.spec.postgresql.conf')"></span>
						</th>
						<th class="actions"></th>
					</thead>
					<tbody>
						<template v-if="config === null">
							<tr class="no-results">
								<td colspan="999">
									Loading data...
								</td>
							</tr>
						</template>
						<template v-else-if="!config.length">
							<tr class="no-results">
								<td colspan="4" v-if="iCan('create','sgpgconfigs',$route.params.namespace)">
									No configurations have been found, would you like to <router-link :to="'/' + $route.params.namespace + '/sgpgconfigs/new'" title="Add New Postgres Configuration">create a new one?</router-link>
								</td>
								<td v-else colspan="4">
									No configurations have been found. You don't have enough permissions to create a new one
								</td>
							</tr>
						</template>
						<template v-else>
							<template v-for="(conf, index) in config">
								<template v-if="(index >= pagination.start) && (index < pagination.end)">
									<tr class="base">
										<td class="hasTooltip configName">
											<span>
												<router-link :to="'/' + $route.params.namespace + '/sgpgconfig/' + conf.name" class="noColor">
													{{ conf.name }}
												</router-link>
											</span>
										</td>
										<td class="pgVersion">
											<router-link :to="'/' + $route.params.namespace + '/sgpgconfig/' + conf.name" class="noColor">
												{{ conf.data.spec.postgresVersion }}
											</router-link>
										</td>
										<td class="parameters hasTooltip">
											<span>
												<router-link :to="'/' + $route.params.namespace + '/sgpgconfig/' + conf.name" class="noColor">
													<template v-for="param in conf.data.status['postgresql.conf']">
														<strong>{{ param.parameter }}:</strong> {{ param.value }}; 
													</template>
												</router-link>
											</span>
										</td>
										<td class="actions">
											<router-link v-if="iCan('patch','sgpgconfigs',$route.params.namespace)" :to="'/' + $route.params.namespace + '/sgpgconfig/' + conf.name + '/edit'" title="Edit Configuration" class="editCRD"></router-link>
											<a v-if="iCan('create','sgpgconfigs',$route.params.namespace)" @click="cloneCRD('SGPostgresConfigs', $route.params.namespace, conf.name)" class="cloneCRD" title="Clone Configuration"></a>
											<a v-if="iCan('delete','sgpgconfigs',$route.params.namespace)" @click="deleteCRD('sgpgconfigs',$route.params.namespace, conf.name)" class="delete deleteCRD" title="Delete Configuration" :class="(conf.data.status.clusters.length || (typeof sgDistributedLogs.find(l => ( (l.data.metadata.namespace == conf.data.metadata.namespace) && (l.data.spec.configurations.sgPostgresConfig == conf.name) ))) != 'undefined')  ? 'disabled' : ''"></a>
										</td>
									</tr>
								</template>
							</template>
						</template>
					</tbody>
				</table>
				<v-page :key="'pagination-'+pagination.rows" v-if="( (config !== null) && (pagination.rows < config.length) )" v-model="pagination.current" :page-size-menu="(pagination.rows > 1) ? [ pagination.rows, pagination.rows*2, pagination.rows*3 ] : [1]" :total-row="config.length" @page-change="pageChange" align="center" ref="page"></v-page>
				<div id="nameTooltip">
					<div class="info"></div>
				</div>
			</template>
			<template v-else>
				<template v-if="config === null">
					<div class="warningText">
						Loading data...
					</div>
				</template>
				<template v-else>
					<h2>Configuration Details</h2>
					
					<div class="configurationDetails">
						<CRDSummary :crd="crd" kind="SGPostgresConfig" :details="true"></CRDSummary>
					</div>
				</template>
			</template>
		</div>
	</div>
</template>

<script>
	import { mixin } from './mixins/mixin'
	import store from '../store'
	import CRDSummary from './forms/summary/CRDSummary.vue'


    export default {
        name: 'SGPgConfigs',

		mixins: [mixin],

		components: {
            CRDSummary
        },

		data: function() {

			return {
				currentSort: {
					param: 'data.metadata.name',
					type: 'alphabetical'
				},
				currentSortDir: 'asc',
			}
		},
		computed: {

			config () {
				return (
					(store.state.sgpgconfigs !== null)
						? this.sortTable( [...(store.state.sgpgconfigs.filter(conf => (conf.data.metadata.namespace == this.$route.params.namespace)))], this.currentSort.param, this.currentSortDir, this.currentSort.type )
						: null
				)
			},

			sgDistributedLogs() {
				return (
					(store.state.sgdistributedlogs !== null)
						? store.state.sgdistributedlogs
						: []
				)
            },

			tooltips() {
				return store.state.tooltips
			},

			crd () {
				return (
					(store.state.sgpgconfigs !== null)
						? store.state.sgpgconfigs.find(c => (c.data.metadata.namespace == this.$route.params.namespace) && (c.data.metadata.name == this.$route.params.name))
						: null
				)
			}

		},

		methods: {

			hasParamsSet(conf) {
				let setParam = conf.data.status['postgresql.conf'].find(p => ( (conf.data.status.defaultParameters[p.parameter] != p.value) ))
				return (typeof setParam != 'undefined')
			}

		}

	}
</script>