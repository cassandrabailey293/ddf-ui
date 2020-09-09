/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/

const wreqr = require('../../js/wreqr.js')
const Marionette = require('marionette')
const Backbone = require('backbone')
const $ = require('jquery')
const template = require('./metacard-menu.hbs')
const CustomElements = require('../../js/CustomElements.js')
const metacardInstance = require('../metacard/metacard.js')
const MetacardTitleView = require('../metacard-title/metacard-title.view.js')

module.exports = Marionette.LayoutView.extend({
  template,
  tagName: CustomElements.register('metacard-menu'),
  regions: {
    metacardTitle: '.metacard-title',
  },
  onFirstRender() {
    this.listenTo(metacardInstance, 'change:currentMetacard', this.render)
  },
  onRender() {
    if (metacardInstance.get('currentMetacard')) {
      this.metacardTitle.show(
        new MetacardTitleView({
          model: new Backbone.Collection(
            metacardInstance.get('currentMetacard')
          ),
        })
      )
    }
  },
  serializeData() {
    let resultJSON
    if (metacardInstance.get('currentMetacard')) {
      resultJSON = metacardInstance.get('currentMetacard').toJSON()
    }
    return {
      result: resultJSON,
    }
  },
})
