<template>
    <div id="sgConfig">
        <template v-if="crd === null">
            <div class="warningText">
                Loading data...
            </div>
        </template>
        <template v-else>
            <div class="content">
                <h2>
                    Operator Configuration Details
                    <router-link
                        v-if="iCan('patch', 'sgconfigs', crd.data.metadata.namespace)"
                        :to="'/sgconfig/' + $route.params.name + '/edit'"
                        title="Edit SGConfig"
                        class="floatRight"
                    >
                        EDIT
                    </router-link>
                </h2>
                        
                <div class="configurationDetails">                      
                    <CRDSummary :crd="crd" kind="SGConfig" :details="true"></CRDSummary>
                </div>
            </div>
        </template>
    </div> 
</template>

<script>
    import store from '@/store'
    import { mixin } from './mixins/mixin'
    import CRDSummary from './forms/summary/CRDSummary.vue'
    
    export default {
        name: 'SGConfig',

		mixins: [mixin],

        components: {
            CRDSummary
        },

       computed: {
            crd() {
                return (store.state.sgconfigs !== null)
                    ? {
                        data: store.state.sgconfigs.find(c => c.metadata.name == this.$route.params.name)
                    }
                    : null
            }
        }
    }
</script>